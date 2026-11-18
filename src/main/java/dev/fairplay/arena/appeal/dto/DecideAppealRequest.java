package dev.fairplay.arena.appeal.dto;

import dev.fairplay.arena.appeal.AppealDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DecideAppealRequest(
        @NotNull AppealDecision decision,
        @NotBlank @Size(min = 1, max = 1000) String note
) {}
