package dev.fairplay.arena.player.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.Role;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerDetailResponse(
        UUID id,
        String username,
        String displayName,
        String country,
        Role role,
        AccountStatus status,
        int rating,
        int matchesPlayed,
        String email,
        Instant createdAt,
        Instant statusChangedAt,
        PlayerStats stats,
        PlayerPercentiles percentiles
) {}
