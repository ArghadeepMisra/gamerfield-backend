package dev.fairplay.arena.match.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.fairplay.arena.match.GameMode;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerMatchResponse(
        UUID matchId,
        String externalId,
        GameMode gameMode,
        String mapName,
        Instant endedAt,
        int placement,
        int participantCount,
        int kills,
        int deaths,
        int headshots,
        int shotsFired,
        int shotsHit,
        int avgReactionMs,
        int ratingBefore,
        int ratingAfter
) {}
