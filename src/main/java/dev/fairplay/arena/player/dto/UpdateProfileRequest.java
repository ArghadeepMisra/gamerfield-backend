package dev.fairplay.arena.player.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 40) String displayName,
        @Pattern(regexp = "^[A-Z]{2}$") String country
) {}
