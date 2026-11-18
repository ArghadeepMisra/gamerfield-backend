package dev.fairplay.arena.moderation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.fairplay.arena.moderation.ActionSource;
import dev.fairplay.arena.moderation.ModerationActionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueueItemResponse(
        UUID playerId,
        String username,
        int rating,
        Instant flaggedAt,
        LatestAction latestAction
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LatestAction(
            UUID id,
            ModerationActionType action,
            ActionSource source,
            String reason,
            BigDecimal confidence,
            boolean requiresHumanReview,
            Instant createdAt
    ) {}
}
