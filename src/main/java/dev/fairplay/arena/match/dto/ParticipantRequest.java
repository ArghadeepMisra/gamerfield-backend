package dev.fairplay.arena.match.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ParticipantRequest(
        @NotNull UUID playerId,
        @Min(1) int placement,
        @Min(0) int kills,
        @Min(0) int deaths,
        @Min(0) int headshots,
        @Min(0) int shotsFired,
        @Min(0) int shotsHit,
        @Min(1) int avgReactionMs
) {}
