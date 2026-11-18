package dev.fairplay.arena.moderation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.moderation.ActionSource;
import dev.fairplay.arena.moderation.ModerationActionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModerationActionResponse(
        UUID id,
        UUID playerId,
        ModerationActionType action,
        AccountStatus fromStatus,
        AccountStatus toStatus,
        ActionSource source,
        String actorUsername,
        String reason,
        List<String> evidence,
        BigDecimal confidence,
        boolean requiresHumanReview,
        Instant createdAt
) {}
