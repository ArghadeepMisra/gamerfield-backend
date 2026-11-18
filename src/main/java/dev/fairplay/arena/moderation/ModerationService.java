package dev.fairplay.arena.moderation;

import dev.fairplay.arena.common.error.ApiException;
import dev.fairplay.arena.common.error.ErrorCode;
import dev.fairplay.arena.common.web.PageResponse;
import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.leaderboard.LeaderboardEvents.RemovedFromLeaderboard;
import dev.fairplay.arena.moderation.dto.ModerationActionRequest;
import dev.fairplay.arena.moderation.dto.ModerationActionResponse;
import dev.fairplay.arena.moderation.dto.QueueItemResponse;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.player.PlayerRepository;
import dev.fairplay.arena.player.Role;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ModerationService {

    private static final Set<ModerationActionType> DIRECT_ACTIONS = EnumSet.of(
            ModerationActionType.FLAG,
            ModerationActionType.BAN,
            ModerationActionType.CLEAR,
            ModerationActionType.NOTE);

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final AccountStateMachine stateMachine = new AccountStateMachine();
    private final PlayerRepository playerRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final ModerationQueueQuery moderationQueueQuery;
    private final ApplicationEventPublisher eventPublisher;
    private final ArenaProperties props;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public ModerationService(
            PlayerRepository playerRepository,
            ModerationActionRepository moderationActionRepository,
            ModerationQueueQuery moderationQueueQuery,
            ApplicationEventPublisher eventPublisher,
            ArenaProperties props,
            JsonMapper jsonMapper,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.playerRepository = playerRepository;
        this.moderationActionRepository = moderationActionRepository;
        this.moderationQueueQuery = moderationQueueQuery;
        this.eventPublisher = eventPublisher;
        this.props = props;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public ModerationActionResponse applyAction(
            UUID targetPlayerId,
            ModerationActionRequest request,
            UUID actorId) {
        if (!DIRECT_ACTIONS.contains(request.action())) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "Action " + request.action() + " cannot be applied via this endpoint");
        }

        Player actor = playerRepository.findById(actorId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Actor not found"));

        enforceGuardrails(actor, request);

        Player target = playerRepository.findById(targetPlayerId)
                .orElseThrow(() -> new ApiException(ErrorCode.PLAYER_NOT_FOUND, "Player not found: " + targetPlayerId));
        if (target.getRole() != Role.PLAYER) {
            throw new ApiException(ErrorCode.TARGET_NOT_A_PLAYER, "Target account is not a player: " + targetPlayerId);
        }

        AccountStatus from = target.getStatus();
        AccountStatus to = stateMachine.next(from, request.action());
        Instant now = Instant.now(clock);

        if (to != from) {
            target.setStatus(to);
            target.setStatusChangedAt(now);
            target.setUpdatedAt(now);
        }

        String evidenceJson = serializeEvidence(request.evidence());
        boolean requiresHumanReview = Boolean.TRUE.equals(request.requiresHumanReview());

        ModerationAction saved = moderationActionRepository.save(new ModerationAction(
                UUID.randomUUID(),
                target,
                request.action(),
                from,
                to,
                actor,
                request.source(),
                request.reason(),
                evidenceJson,
                request.confidence(),
                requiresHumanReview,
                now));

        if (to == AccountStatus.BANNED && from != AccountStatus.BANNED) {
            eventPublisher.publishEvent(new RemovedFromLeaderboard(target.getId()));
        }

        meterRegistry.counter(
                "arena.moderation.actions",
                "action", request.action().name(),
                "source", request.source().name()).increment();

        log.info("Moderation action {} on player {} ({} -> {}) source={} actor={}",
                request.action(), target.getId(), from, to, request.source(), actor.getUsername());

        return toResponse(saved, target.getId(), actor.getUsername(), request.evidence());
    }

    @Transactional(readOnly = true)
    public PageResponse<QueueItemResponse> getQueue(boolean humanReviewOnly, int page, int size) {
        return moderationQueueQuery.fetchQueue(humanReviewOnly, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<ModerationActionResponse> getHistory(UUID playerId, int page, int size) {
        if (!playerRepository.existsById(playerId)) {
            throw new ApiException(ErrorCode.PLAYER_NOT_FOUND, "Player not found: " + playerId);
        }
        Page<ModerationActionResponse> results = moderationActionRepository
                .findHistoryByPlayerId(playerId, PageRequest.of(page, size))
                .map(a -> toResponse(a, playerId, a.getActor().getUsername(), deserializeEvidence(a.getEvidenceJson())));
        return PageResponse.from(results);
    }

    private void enforceGuardrails(Player actor, ModerationActionRequest request) {
        if (actor.getRole() == Role.MODERATOR) {
            if (request.source() != ActionSource.HUMAN) {
                throw new ApiException(ErrorCode.INVALID_SOURCE, "Moderators must use source HUMAN");
            }
        } else if (actor.getRole() == Role.SERVICE) {
            if (request.source() != ActionSource.TELEMETRY && request.source() != ActionSource.JUDGE) {
                throw new ApiException(ErrorCode.INVALID_SOURCE, "Service accounts must use TELEMETRY or JUDGE");
            }
            if (request.action() == ModerationActionType.BAN) {
                if (request.confidence() == null) {
                    throw new ApiException(ErrorCode.CONFIDENCE_REQUIRED, "Service BAN requires confidence");
                }
                if (request.confidence().compareTo(props.moderation().serviceBanMinConfidence()) < 0) {
                    throw new ApiException(
                            ErrorCode.CONFIDENCE_TOO_LOW,
                            "Service BAN confidence must be at least " + props.moderation().serviceBanMinConfidence());
                }
            }
        } else {
            throw new ApiException(ErrorCode.FORBIDDEN, "Insufficient permissions");
        }
    }

    private String serializeEvidence(List<String> evidence) {
        if (evidence == null) {
            return null;
        }
        return jsonMapper.writeValueAsString(evidence);
    }

    private List<String> deserializeEvidence(String evidenceJson) {
        if (evidenceJson == null) {
            return null;
        }
        return jsonMapper.readValue(evidenceJson, STRING_LIST_TYPE);
    }

    private ModerationActionResponse toResponse(
            ModerationAction action,
            UUID playerId,
            String actorUsername,
            List<String> evidence) {
        return new ModerationActionResponse(
                action.getId(),
                playerId,
                action.getAction(),
                action.getFromStatus(),
                action.getToStatus(),
                action.getSource(),
                actorUsername,
                action.getReason(),
                evidence,
                action.getConfidence(),
                action.isRequiresHumanReview(),
                action.getCreatedAt());
    }
}
