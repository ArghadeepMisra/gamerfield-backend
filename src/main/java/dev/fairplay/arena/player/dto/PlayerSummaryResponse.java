package dev.fairplay.arena.player.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.Role;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerSummaryResponse(
        UUID id,
        String username,
        String displayName,
        String country,
        Role role,
        AccountStatus status,
        int rating,
        int matchesPlayed
) {}
