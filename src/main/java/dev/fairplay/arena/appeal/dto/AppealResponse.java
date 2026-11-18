package dev.fairplay.arena.appeal.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.fairplay.arena.appeal.AppealStatus;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppealResponse(
        UUID id,
        UUID playerId,
        String username,
        UUID banActionId,
        AppealStatus status,
        String statement,
        Instant createdAt,
        UUID decidedBy,
        String decisionNote,
        Instant decidedAt
) {}
