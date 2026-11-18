package dev.fairplay.arena.leaderboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlayerRankResponse(
        UUID playerId,
        String username,
        int rating,
        long rank,
        long totalRanked,
        String source
) {}
