package dev.fairplay.arena.leaderboard.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LeaderboardPage(
        long total,
        int offset,
        int limit,
        List<LeaderboardEntry> entries
) {}
