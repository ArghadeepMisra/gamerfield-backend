package dev.fairplay.arena.leaderboard;

public final class RatingCalculator {

    public static final int DEFAULT_MAX_DELTA = 30;

    private RatingCalculator() {}

    public static int delta(int participantCount, int placement) {
        return delta(participantCount, placement, DEFAULT_MAX_DELTA);
    }

    public static int delta(int participantCount, int placement, int maxDelta) {
        if (participantCount < 2) {
            throw new IllegalArgumentException("participantCount must be at least 2");
        }
        if (placement < 1 || placement > participantCount) {
            throw new IllegalArgumentException("placement must be between 1 and " + participantCount);
        }
        double numerator = participantCount + 1.0 - (2.0 * placement);
        double denominator = participantCount - 1.0;
        return (int) Math.round(maxDelta * (numerator / denominator));
    }

    public static int applyDelta(int currentRating, int delta) {
        return Math.max(0, currentRating + delta);
    }
}
