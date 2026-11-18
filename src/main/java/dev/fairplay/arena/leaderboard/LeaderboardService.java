package dev.fairplay.arena.leaderboard;

import dev.fairplay.arena.common.error.ApiException;
import dev.fairplay.arena.common.error.ErrorCode;
import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.leaderboard.dto.LeaderboardEntry;
import dev.fairplay.arena.leaderboard.dto.LeaderboardPage;
import dev.fairplay.arena.leaderboard.dto.PlayerRankResponse;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.player.PlayerRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    private static final Set<AccountStatus> ELIGIBLE_STATUSES =
            Set.of(AccountStatus.ACTIVE, AccountStatus.FLAGGED, AccountStatus.REINSTATED);

    private final StringRedisTemplate redisTemplate;
    private final PlayerRepository playerRepository;
    private final JdbcClient jdbcClient;
    private final String leaderboardKey;
    private final MeterRegistry meterRegistry;
    private final Timer redisLookupTimer;
    private final Timer dbLookupTimer;

    public LeaderboardService(
            StringRedisTemplate redisTemplate,
            PlayerRepository playerRepository,
            JdbcClient jdbcClient,
            ArenaProperties props,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.playerRepository = playerRepository;
        this.jdbcClient = jdbcClient;
        this.leaderboardKey = props.leaderboard().key();
        this.meterRegistry = meterRegistry;
        this.redisLookupTimer = Timer.builder("arena.leaderboard.rank.lookup")
                .tag("source", "redis")
                .register(meterRegistry);
        this.dbLookupTimer = Timer.builder("arena.leaderboard.rank.lookup")
                .tag("source", "db")
                .register(meterRegistry);
        Gauge.builder("arena.leaderboard.size", this, LeaderboardService::currentLeaderboardSize)
                .description("Current number of ranked players in Redis")
                .register(meterRegistry);
    }

    @Transactional(readOnly = true)
    public LeaderboardPage getLeaderboard(int offset, int limit) {
        Long zCard = redisTemplate.opsForZSet().zCard(leaderboardKey);
        long total = zCard != null ? zCard : 0L;

        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(leaderboardKey, offset, (long) offset + limit - 1);
        if (tuples == null || tuples.isEmpty()) {
            return new LeaderboardPage(total, offset, limit, List.of());
        }

        List<UUID> orderedIds = new ArrayList<>(tuples.size());
        List<Integer> scores = new ArrayList<>(tuples.size());
        for (TypedTuple<String> tuple : tuples) {
            if (tuple.getValue() != null && tuple.getScore() != null) {
                orderedIds.add(UUID.fromString(tuple.getValue()));
                scores.add((int) Math.round(tuple.getScore()));
            }
        }

        Map<UUID, Player> playersById = playerRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));

        List<LeaderboardEntry> entries = new ArrayList<>(orderedIds.size());
        for (int i = 0; i < orderedIds.size(); i++) {
            UUID playerId = orderedIds.get(i);
            Player player = playersById.get(playerId);
            if (player != null) {
                entries.add(new LeaderboardEntry(
                        offset + i + 1,
                        playerId,
                        player.getUsername(),
                        player.getDisplayName(),
                        scores.get(i)));
            }
        }
        return new LeaderboardPage(total, offset, limit, entries);
    }

    @Transactional(readOnly = true)
    public PlayerRankResponse getPlayerRank(UUID playerId, String source) {
        if (!"redis".equals(source) && !"db".equals(source)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "source must be 'redis' or 'db'");
        }
        Timer timer = "redis".equals(source) ? redisLookupTimer : dbLookupTimer;
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            return "redis".equals(source) ? lookupFromRedis(playerId) : lookupFromDb(playerId);
        } finally {
            sample.stop(timer);
        }
    }

    private PlayerRankResponse lookupFromRedis(UUID playerId) {
        Player player = playerRepository.findById(playerId)
                .filter(Player::isEligibleForMatchAndRanking)
                .orElseThrow(() -> new ApiException(ErrorCode.PLAYER_NOT_RANKED, "Player is not ranked: " + playerId));

        String member = playerId.toString();
        Long zeroBasedRank = redisTemplate.opsForZSet().reverseRank(leaderboardKey, member);
        Double score = redisTemplate.opsForZSet().score(leaderboardKey, member);
        Long zCard = redisTemplate.opsForZSet().zCard(leaderboardKey);

        if (zeroBasedRank == null || score == null || zCard == null) {
            throw new ApiException(ErrorCode.PLAYER_NOT_RANKED, "Player is not ranked: " + playerId);
        }
        return new PlayerRankResponse(
                playerId,
                player.getUsername(),
                (int) Math.round(score),
                zeroBasedRank + 1,
                zCard,
                "redis");
    }

    private PlayerRankResponse lookupFromDb(UUID playerId) {
        Player player = playerRepository.findById(playerId)
                .filter(Player::isEligibleForMatchAndRanking)
                .orElseThrow(() -> new ApiException(ErrorCode.PLAYER_NOT_RANKED, "Player is not ranked: " + playerId));

        Long rank = jdbcClient.sql("""
                SELECT COUNT(*) + 1
                FROM players p
                WHERE p.role = 'PLAYER'
                  AND p.status IN ('ACTIVE', 'FLAGGED', 'REINSTATED')
                  AND (p.rating > :rating
                       OR (p.rating = :rating AND (p.id::text COLLATE "C") > :idText))
                """)
                .param("rating", player.getRating())
                .param("idText", playerId.toString())
                .query(Long.class)
                .single();

        long totalRanked = playerRepository.countEligibleForLeaderboard(ELIGIBLE_STATUSES);
        return new PlayerRankResponse(
                playerId,
                player.getUsername(),
                player.getRating(),
                rank,
                totalRanked,
                "db");
    }

    private double currentLeaderboardSize() {
        try {
            Long size = redisTemplate.opsForZSet().zCard(leaderboardKey);
            return size != null ? size.doubleValue() : 0.0;
        } catch (Exception ex) {
            return 0.0;
        }
    }
}
