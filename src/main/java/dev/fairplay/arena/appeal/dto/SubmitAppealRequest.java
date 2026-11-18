package dev.fairplay.arena.appeal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitAppealRequest(
        @NotBlank @Size(min = 20, max = 2000) String statement
) {}
