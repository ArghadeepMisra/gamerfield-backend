package dev.fairplay.arena.leaderboard;

import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.PlayerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class LeaderboardRebuilder {

    private static final Set<AccountStatus> ELIGIBLE_STATUSES =
            Set.of(AccountStatus.ACTIVE, AccountStatus.FLAGGED, AccountStatus.REINSTATED);

    private record RankedRow(UUID id, int rating) {}

    private final PlayerRepository playerRepository;
    private final JdbcClient jdbcClient;
    private final StringRedisTemplate redisTemplate;
    private final String leaderboardKey;
    private final boolean rebuildOnStartup;
    private final int batchSize;

    public LeaderboardRebuilder(
            PlayerRepository playerRepository,
            JdbcClient jdbcClient,
            StringRedisTemplate redisTemplate,
            ArenaProperties props) {
        this.playerRepository = playerRepository;
        this.jdbcClient = jdbcClient;
        this.redisTemplate = redisTemplate;
        this.leaderboardKey = props.leaderboard().key();
        this.rebuildOnStartup = props.leaderboard().rebuildOnStartup();
        this.batchSize = props.leaderboard().rebuildBatchSize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (rebuildOnStartup) {
            rebuild();
        }
    }

    public void rebuild() {
        long startNanos = System.nanoTime();
        long eligibleCount = playerRepository.countEligibleForLeaderboard(ELIGIBLE_STATUSES);
        Long currentSize = redisTemplate.opsForZSet().zCard(leaderboardKey);
        if (currentSize != null && currentSize == eligibleCount) {
            log.info("Skipping leaderboard rebuild; Redis ZCARD {} matches eligible player count {}",
                    currentSize, eligibleCount);
            return;
        }

        String tempKey = leaderboardKey + ":rebuild";
        redisTemplate.delete(tempKey);

        if (eligibleCount == 0) {
            redisTemplate.delete(leaderboardKey);
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("Leaderboard rebuilt with 0 members in {} ms", durationMs);
            return;
        }

        byte[] tempKeyBytes = tempKey.getBytes(StandardCharsets.UTF_8);
        UUID lastId = null;
        long loaded = 0;

        while (true) {
            List<RankedRow> batch = fetchBatch(lastId);
            if (batch.isEmpty()) {
                break;
            }
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (RankedRow row : batch) {
                    connection.zSetCommands().zAdd(
                            tempKeyBytes,
                            row.rating(),
                            row.id().toString().getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            loaded += batch.size();
            lastId = batch.getLast().id();
            if (batch.size() < batchSize) {
                break;
            }
        }

        redisTemplate.rename(tempKey, leaderboardKey);
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("Leaderboard rebuilt with {} members in {} ms", loaded, durationMs);
    }

    private List<RankedRow> fetchBatch(UUID lastId) {
        if (lastId == null) {
            return jdbcClient.sql("""
                    SELECT id, rating
                    FROM players
                    WHERE role = 'PLAYER'
                      AND status IN ('ACTIVE', 'FLAGGED', 'REINSTATED')
                    ORDER BY id ASC
                    LIMIT :limit
                    """)
                    .param("limit", batchSize)
                    .query((rs, rowNum) -> new RankedRow(
                            rs.getObject("id", UUID.class),
                            rs.getInt("rating")))
                    .list();
        }
        return jdbcClient.sql("""
                SELECT id, rating
                FROM players
                WHERE role = 'PLAYER'
                  AND status IN ('ACTIVE', 'FLAGGED', 'REINSTATED')
                  AND id > :lastId
                ORDER BY id ASC
                LIMIT :limit
                """)
                .param("lastId", lastId)
                .param("limit", batchSize)
                .query((rs, rowNum) -> new RankedRow(
                        rs.getObject("id", UUID.class),
                        rs.getInt("rating")))
                .list();
    }
}
