package dev.fairplay.arena.moderation.dto;

import dev.fairplay.arena.moderation.ActionSource;
import dev.fairplay.arena.moderation.ModerationActionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ModerationActionRequest(
        @NotNull ModerationActionType action,
        @NotNull ActionSource source,
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 20) List<@NotBlank @Size(max = 500) String> evidence,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
        Boolean requiresHumanReview
) {}
