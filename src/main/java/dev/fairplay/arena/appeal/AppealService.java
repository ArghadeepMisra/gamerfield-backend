package dev.fairplay.arena.appeal;

import dev.fairplay.arena.appeal.dto.AppealResponse;
import dev.fairplay.arena.appeal.dto.DecideAppealRequest;
import dev.fairplay.arena.appeal.dto.SubmitAppealRequest;
import dev.fairplay.arena.common.error.ApiException;
import dev.fairplay.arena.common.error.ErrorCode;
import dev.fairplay.arena.common.web.PageResponse;
import dev.fairplay.arena.leaderboard.LeaderboardEvents.RatingChanged;
import dev.fairplay.arena.moderation.AccountStateMachine;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.moderation.ActionSource;
import dev.fairplay.arena.moderation.ModerationAction;
import dev.fairplay.arena.moderation.ModerationActionRepository;
import dev.fairplay.arena.moderation.ModerationActionType;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.player.PlayerRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class AppealService {

    private final AccountStateMachine stateMachine = new AccountStateMachine();
    private final AppealRepository appealRepository;
    private final PlayerRepository playerRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public AppealService(
            AppealRepository appealRepository,
            PlayerRepository playerRepository,
            ModerationActionRepository moderationActionRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.appealRepository = appealRepository;
        this.playerRepository = playerRepository;
        this.moderationActionRepository = moderationActionRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public AppealResponse submitAppeal(UUID playerId, SubmitAppealRequest request) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ApiException(ErrorCode.PLAYER_NOT_FOUND, "Player not found: " + playerId));

        if (player.getStatus() != AccountStatus.BANNED) {
            throw new ApiException(ErrorCode.APPEAL_NOT_ALLOWED, "Only BANNED players may submit an appeal");
        }

        ModerationAction banAction = moderationActionRepository
                .findFirstByPlayerIdAndActionOrderByCreatedAtDesc(playerId, ModerationActionType.BAN)
                .orElseThrow(() -> new ApiException(ErrorCode.APPEAL_NOT_ALLOWED, "No ban action found for player"));

        if (appealRepository.existsByBanActionId(banAction.getId())) {
            throw new ApiException(ErrorCode.APPEAL_ALREADY_EXISTS, "This ban has already been appealed");
        }

        Instant now = Instant.now(clock);
        AccountStatus from = player.getStatus();
        AccountStatus to = stateMachine.next(from, ModerationActionType.APPEAL_SUBMITTED);

        Appeal appeal = appealRepository.save(new Appeal(
                UUID.randomUUID(),
                player,
                banAction,
                AppealStatus.PENDING,
                request.statement(),
                now));

        player.setStatus(to);
        player.setStatusChangedAt(now);
        player.setUpdatedAt(now);

        moderationActionRepository.save(new ModerationAction(
                UUID.randomUUID(),
                player,
                ModerationActionType.APPEAL_SUBMITTED,
                from,
                to,
                player,
                ActionSource.HUMAN,
                "Appeal submitted",
                null,
                null,
                false,
                now));

        meterRegistry.counter(
                "arena.moderation.actions",
                "action", ModerationActionType.APPEAL_SUBMITTED.name(),
                "source", ActionSource.HUMAN.name()).increment();

        log.info("Moderation action {} on player {} ({} -> {}) source={} actor={}",
                ModerationActionType.APPEAL_SUBMITTED, player.getId(), from, to, ActionSource.HUMAN, player.getUsername());

        return toResponse(appeal);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppealResponse> listAppeals(AppealStatus status, int page, int size) {
        Page<AppealResponse> results = appealRepository
                .findByStatusOrderByCreatedAtAsc(status, PageRequest.of(page, size))
                .map(AppealService::toResponse);
        return PageResponse.from(results);
    }

    @Transactional
    public AppealResponse decideAppeal(UUID appealId, DecideAppealRequest request, UUID moderatorId) {
        Appeal appeal = appealRepository.findByIdWithDetails(appealId)
                .orElseThrow(() -> new ApiException(ErrorCode.APPEAL_NOT_FOUND, "Appeal not found: " + appealId));

        if (appeal.getStatus() != AppealStatus.PENDING) {
            throw new ApiException(ErrorCode.APPEAL_ALREADY_DECIDED, "Appeal has already been decided");
        }

        Player moderator = playerRepository.findById(moderatorId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Moderator not found"));
        Player player = appeal.getPlayer();

        Instant now = Instant.now(clock);
        AppealStatus newAppealStatus = request.decision() == AppealDecision.UPHOLD
                ? AppealStatus.UPHELD
                : AppealStatus.OVERTURNED;
        ModerationActionType actionType = request.decision() == AppealDecision.UPHOLD
                ? ModerationActionType.APPEAL_UPHELD
                : ModerationActionType.APPEAL_OVERTURNED;

        AccountStatus from = player.getStatus();
        AccountStatus to = stateMachine.next(from, actionType);

        appeal.setStatus(newAppealStatus);
        appeal.setDecidedBy(moderator);
        appeal.setDecisionNote(request.note());
        appeal.setDecidedAt(now);

        player.setStatus(to);
        player.setStatusChangedAt(now);
        player.setUpdatedAt(now);

        moderationActionRepository.save(new ModerationAction(
                UUID.randomUUID(),
                player,
                actionType,
                from,
                to,
                moderator,
                ActionSource.HUMAN,
                request.note(),
                null,
                null,
                false,
                now));

        if (to == AccountStatus.REINSTATED) {
            eventPublisher.publishEvent(new RatingChanged(player.getId(), player.getRating()));
        }

        meterRegistry.counter(
                "arena.moderation.actions",
                "action", actionType.name(),
                "source", ActionSource.HUMAN.name()).increment();
        meterRegistry.counter(
                "arena.appeals.decided",
                "decision", request.decision().name()).increment();

        log.info("Moderation action {} on player {} ({} -> {}) source={} actor={}",
                actionType, player.getId(), from, to, ActionSource.HUMAN, moderator.getUsername());

        return toResponse(appeal);
    }

    private static AppealResponse toResponse(Appeal appeal) {
        return new AppealResponse(
                appeal.getId(),
                appeal.getPlayer().getId(),
                appeal.getPlayer().getUsername(),
                appeal.getBanAction().getId(),
                appeal.getStatus(),
                appeal.getStatement(),
                appeal.getCreatedAt(),
                appeal.getDecidedBy() != null ? appeal.getDecidedBy().getId() : null,
                appeal.getDecisionNote(),
                appeal.getDecidedAt());
    }
}
