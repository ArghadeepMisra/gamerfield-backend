package dev.fairplay.arena.match.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.fairplay.arena.match.GameMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MatchResponse(
        UUID id,
        String externalId,
        GameMode gameMode,
        String mapName,
        Instant startedAt,
        Instant endedAt,
        List<ParticipantResponse> participants
) {}
