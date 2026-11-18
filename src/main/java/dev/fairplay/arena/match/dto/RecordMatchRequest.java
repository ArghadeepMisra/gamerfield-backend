package dev.fairplay.arena.match.dto;

import dev.fairplay.arena.match.GameMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record RecordMatchRequest(
        @NotBlank @Size(max = 64) String externalId,
        @NotNull GameMode gameMode,
        @NotBlank @Size(max = 40) String mapName,
        @NotNull Instant startedAt,
        @NotNull Instant endedAt,
        @NotEmpty @Size(min = 2, max = 100) @Valid List<ParticipantRequest> participants
) {}
