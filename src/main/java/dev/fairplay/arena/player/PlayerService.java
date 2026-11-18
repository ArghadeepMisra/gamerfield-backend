package dev.fairplay.arena.player;

import dev.fairplay.arena.common.error.ApiException;
import dev.fairplay.arena.common.error.ErrorCode;
import dev.fairplay.arena.common.web.PageResponse;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.dto.PlayerDetailResponse;
import dev.fairplay.arena.player.dto.PlayerPercentiles;
import dev.fairplay.arena.player.dto.PlayerStats;
import dev.fairplay.arena.player.dto.PlayerSummaryResponse;
import dev.fairplay.arena.player.dto.UpdateProfileRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;
    private final StatDistributionService statDistributionService;
    private final Clock clock;

    public PlayerService(
            PlayerRepository playerRepository,
            PlayerMapper playerMapper,
            StatDistributionService statDistributionService,
            Clock clock) {
        this.playerRepository = playerRepository;
        this.playerMapper = playerMapper;
        this.statDistributionService = statDistributionService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PlayerDetailResponse getMe(UUID playerId) {
        Player player = findOrThrow(playerId);
        return toDetail(player, true);
    }

    @Transactional
    public PlayerDetailResponse updateMe(UUID playerId, UpdateProfileRequest request) {
        Player player = findOrThrow(playerId);
        if (player.getStatus() == AccountStatus.BANNED || player.getStatus() == AccountStatus.APPEALED) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, "Suspended accounts cannot update their profile");
        }
        boolean changed = false;
        if (request.displayName() != null) {
            player.setDisplayName(request.displayName());
            changed = true;
        }
        if (request.country() != null) {
            player.setCountry(request.country());
            changed = true;
        }
        if (changed) {
            player.setUpdatedAt(Instant.now(clock));
        }
        return toDetail(player, true);
    }

    @Transactional(readOnly = true)
    public PlayerDetailResponse getById(UUID id) {
        Player player = findOrThrow(id);
        return toDetail(player, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlayerSummaryResponse> search(String query, AccountStatus status, int page, int size) {
        String escapedPrefix = null;
        if (query != null && !query.isBlank()) {
            escapedPrefix = query.toLowerCase(Locale.ROOT)
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
        }
        Page<PlayerSummaryResponse> results = playerRepository
                .searchPlayers(escapedPrefix, status, PageRequest.of(page, size))
                .map(playerMapper::toSummary);
        return PageResponse.from(results);
    }

    public PlayerDetailResponse toDetail(Player player, boolean includeEmail) {
        PlayerStats stats = computeStats(player);
        PlayerPercentiles percentiles = player.getRole() == Role.PLAYER
                ? statDistributionService.percentilesFor(
                        player.getMatchesPlayed(),
                        stats.headshotRate(),
                        stats.accuracy(),
                        stats.avgReactionMs())
                : null;
        return playerMapper.toDetail(
                player,
                includeEmail ? player.getEmail() : null,
                stats,
                percentiles);
    }

    public static PlayerStats computeStats(Player player) {
        long kills = player.getTotalKills();
        long deaths = player.getTotalDeaths();
        double kdRatio = round4((double) kills / Math.max(deaths, 1L));
        double headshotRate = kills == 0 ? 0.0 : round4((double) player.getTotalHeadshots() / kills);
        long shotsFired = player.getTotalShotsFired();
        double accuracy = shotsFired == 0 ? 0.0 : round4((double) player.getTotalShotsHit() / shotsFired);
        Integer avgReactionMs = player.getMatchesPlayed() == 0
                ? null
                : (int) (player.getSumReactionMs() / player.getMatchesPlayed());
        return new PlayerStats(kills, deaths, kdRatio, headshotRate, accuracy, avgReactionMs);
    }

    private Player findOrThrow(UUID id) {
        return playerRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.PLAYER_NOT_FOUND, "Player not found: " + id));
    }

    private static double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
