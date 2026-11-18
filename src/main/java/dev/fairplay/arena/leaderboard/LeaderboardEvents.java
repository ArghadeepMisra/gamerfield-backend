package dev.fairplay.arena.leaderboard;

import java.util.UUID;

public final class LeaderboardEvents {

    private LeaderboardEvents() {}

    public record RatingChanged(UUID playerId, int rating) {}

    public record RemovedFromLeaderboard(UUID playerId) {}
}
