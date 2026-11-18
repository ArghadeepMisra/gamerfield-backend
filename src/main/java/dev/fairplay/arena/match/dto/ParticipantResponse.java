package dev.fairplay.arena.match.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParticipantResponse(
        UUID playerId,
        String username,
        int placement,
        int kills,
        int deaths,
        int headshots,
        int shotsFired,
        int shotsHit,
        int avgReactionMs,
        int ratingBefore,
        int ratingAfter
) {}
