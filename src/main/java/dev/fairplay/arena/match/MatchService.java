package dev.fairplay.arena.match;

import dev.fairplay.arena.common.error.ApiException;
import dev.fairplay.arena.common.error.ErrorCode;
import dev.fairplay.arena.common.web.PageResponse;
import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.leaderboard.LeaderboardEvents.RatingChanged;
import dev.fairplay.arena.leaderboard.RatingCalculator;
import dev.fairplay.arena.match.dto.MatchResponse;
import dev.fairplay.arena.match.dto.ParticipantRequest;
import dev.fairplay.arena.match.dto.ParticipantResponse;
import dev.fairplay.arena.match.dto.PlayerMatchResponse;
import dev.fairplay.arena.match.dto.RecordMatchRequest;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.player.PlayerRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MatchService {

    public record RecordResult(MatchResponse match, boolean created) {}

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository participantRepository;
    private final PlayerRepository playerRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ArenaProperties props;
    private final Clock clock;
    private final Counter matchesRecorded;
    private final TransactionTemplate recordTx;

    public MatchService(
            MatchRepository matchRepository,
            MatchParticipantRepository participantRepository,
            PlayerRepository playerRepository,
            ApplicationEventPublisher eventPublisher,
            ArenaProperties props,
            Clock clock,
            MeterRegistry meterRegistry,
            PlatformTransactionManager txManager) {
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
        this.playerRepository = playerRepository;
        this.eventPublisher = eventPublisher;
        this.props = props;
        this.clock = clock;
        this.recordTx = new TransactionTemplate(txManager);
        this.matchesRecorded = Counter.builder("arena.matches.recorded")
                .description("Number of matches recorded")
                .register(meterRegistry);
    }

    /**
     * Records a match in one transaction. Retries with the same {@code externalId} are idempotent.
     *
     * <p>Two concurrent requests with the same {@code externalId} can both pass the existence
     * check inside the transaction; the loser then violates the unique constraint on
     * {@code matches.external_id} when it flushes. PostgreSQL aborts that transaction, so the replay
     * lookup must happen after it has rolled back. The transaction is therefore driven by a
     * {@link TransactionTemplate} (not {@code @Transactional}) so the violation can be caught outside it
     * and answered with the winner's match (200) instead of a 500.
     */
    public RecordResult recordMatch(RecordMatchRequest request, UUID recorderId) {
        try {
            return recordTx.execute(status -> doRecordMatch(request, recorderId));
        } catch (DataIntegrityViolationException ex) {
            Optional<Match> winner = matchRepository.findByExternalId(request.externalId());
            if (winner.isPresent()) {
                return new RecordResult(toResponse(winner.get()), false);
            }
            throw ex;
        }
    }

    private RecordResult doRecordMatch(RecordMatchRequest request, UUID recorderId) {
        validateRequest(request);

        Optional<Match> existing = matchRepository.findByExternalId(request.externalId());
        if (existing.isPresent()) {
            return new RecordResult(toResponse(existing.get()), false);
        }

        List<UUID> sortedIds = request.participants().stream()
                .map(ParticipantRequest::playerId)
                .sorted()
                .toList();

        List<Player> lockedPlayers = playerRepository.findAllByIdInForUpdate(sortedIds);
        if (lockedPlayers.size() != sortedIds.size()) {
            Set<UUID> foundIds = lockedPlayers.stream().map(Player::getId).collect(Collectors.toSet());
            List<UUID> missing = sortedIds.stream().filter(id -> !foundIds.contains(id)).toList();
            throw new ApiException(ErrorCode.PLAYER_NOT_FOUND, "Unknown player IDs: " + missing);
        }

        List<UUID> ineligible = lockedPlayers.stream()
                .filter(p -> !p.isEligibleForMatchAndRanking())
                .map(Player::getId)
                .toList();
        if (!ineligible.isEmpty()) {
            throw new ApiException(ErrorCode.PLAYER_NOT_ELIGIBLE, "Players not eligible for match: " + ineligible);
        }

        Map<UUID, Player> playersById = lockedPlayers.stream()
                .collect(Collectors.toMap(Player::getId, Function.identity()));

        Instant now = Instant.now(clock);
        Player recorder = playerRepository.getReferenceById(recorderId);
        Match match = new Match(
                UUID.randomUUID(),
                request.externalId(),
                request.gameMode(),
                request.mapName(),
                request.startedAt(),
                request.endedAt(),
                recorder,
                now);
        matchRepository.saveAndFlush(match);

        int participantCount = request.participants().size();
        int maxDelta = props.rating().maxDelta();
        List<MatchParticipant> savedParticipants = new ArrayList<>(participantCount);

        for (ParticipantRequest pReq : request.participants()) {
            Player player = playersById.get(pReq.playerId());
            int ratingBefore = player.getRating();
            int delta = RatingCalculator.delta(participantCount, pReq.placement(), maxDelta);
            int ratingAfter = RatingCalculator.applyDelta(ratingBefore, delta);

            player.setRating(ratingAfter);
            player.setMatchesPlayed(player.getMatchesPlayed() + 1);
            player.setTotalKills(player.getTotalKills() + pReq.kills());
            player.setTotalDeaths(player.getTotalDeaths() + pReq.deaths());
            player.setTotalHeadshots(player.getTotalHeadshots() + pReq.headshots());
            player.setTotalShotsFired(player.getTotalShotsFired() + pReq.shotsFired());
            player.setTotalShotsHit(player.getTotalShotsHit() + pReq.shotsHit());
            player.setSumReactionMs(player.getSumReactionMs() + pReq.avgReactionMs());
            player.setUpdatedAt(now);

            MatchParticipant mp = new MatchParticipant(
                    match,
                    player,
                    pReq.placement(),
                    pReq.kills(),
                    pReq.deaths(),
                    pReq.headshots(),
                    pReq.shotsFired(),
                    pReq.shotsHit(),
                    pReq.avgReactionMs(),
                    ratingBefore,
                    ratingAfter);
            savedParticipants.add(mp);
            eventPublisher.publishEvent(new RatingChanged(player.getId(), ratingAfter));
        }
        participantRepository.saveAll(savedParticipants);

        matchesRecorded.increment();
        log.info("Recorded match {} with {} participants", match.getId(), participantCount);

        List<ParticipantResponse> participantResponses = savedParticipants.stream()
                .sorted(Comparator.comparingInt(MatchParticipant::getPlacement))
                .map(MatchService::toParticipantResponse)
                .toList();
        return new RecordResult(
                new MatchResponse(
                        match.getId(),
                        match.getExternalId(),
                        match.getGameMode(),
                        match.getMapName(),
                        match.getStartedAt(),
                        match.getEndedAt(),
                        participantResponses),
                true);
    }

    @Transactional(readOnly = true)
    public MatchResponse getMatch(UUID matchId) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ApiException(ErrorCode.MATCH_NOT_FOUND, "Match not found: " + matchId));
        return toResponse(match);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlayerMatchResponse> getPlayerMatches(UUID playerId, int page, int size) {
        if (!playerRepository.existsById(playerId)) {
            throw new ApiException(ErrorCode.PLAYER_NOT_FOUND, "Player not found: " + playerId);
        }
        return PageResponse.from(participantRepository.findPlayerMatches(playerId, PageRequest.of(page, size)));
    }

    private MatchResponse toResponse(Match match) {
        List<ParticipantResponse> participants = participantRepository
                .findByMatchIdWithPlayerOrderByPlacement(match.getId())
                .stream()
                .map(MatchService::toParticipantResponse)
                .toList();
        return new MatchResponse(
                match.getId(),
                match.getExternalId(),
                match.getGameMode(),
                match.getMapName(),
                match.getStartedAt(),
                match.getEndedAt(),
                participants);
    }

    private static ParticipantResponse toParticipantResponse(MatchParticipant mp) {
        return new ParticipantResponse(
                mp.getPlayer().getId(),
                mp.getPlayer().getUsername(),
                mp.getPlacement(),
                mp.getKills(),
                mp.getDeaths(),
                mp.getHeadshots(),
                mp.getShotsFired(),
                mp.getShotsHit(),
                mp.getAvgReactionMs(),
                mp.getRatingBefore(),
                mp.getRatingAfter());
    }

    private static void validateRequest(RecordMatchRequest request) {
        if (!request.endedAt().isAfter(request.startedAt())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "endedAt must be after startedAt");
        }
        int n = request.participants().size();
        Set<UUID> seenPlayers = new HashSet<>(n);
        Set<Integer> seenPlacements = new HashSet<>(n);
        for (ParticipantRequest p : request.participants()) {
            if (!seenPlayers.add(p.playerId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Duplicate playerId: " + p.playerId());
            }
            if (p.placement() < 1 || p.placement() > n || !seenPlacements.add(p.placement())) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "Placements must form the exact set 1.." + n);
            }
            if (p.headshots() > p.kills()) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "headshots cannot exceed kills for player " + p.playerId());
            }
            if (p.shotsHit() > p.shotsFired()) {
                throw new ApiException(
                        ErrorCode.VALIDATION_FAILED,
                        "shotsHit cannot exceed shotsFired for player " + p.playerId());
            }
        }
    }
}
