package dev.fairplay.arena.leaderboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaderboardEntry(
        int rank,
        UUID playerId,
        String username,
        String displayName,
        int rating
) {}
