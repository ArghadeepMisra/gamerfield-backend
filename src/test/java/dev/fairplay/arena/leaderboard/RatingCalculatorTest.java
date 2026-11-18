package dev.fairplay.arena.leaderboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RatingCalculatorTest {

    @ParameterizedTest
    @CsvSource({
            "2, 1, 30",
            "2, 2, -30",
            "4, 1, 30",
            "4, 2, 10",
            "4, 3, -10",
            "4, 4, -30",
            "10, 1, 30",
            "10, 5, 3",
            "10, 6, -3",
            "10, 10, -30"
    })
    void calculatesExactDeltasFromSpec(int n, int placement, int expectedDelta) {
        assertThat(RatingCalculator.delta(n, placement)).isEqualTo(expectedDelta);
    }

    @Test
    void floorsRatingAtZero() {
        assertThat(RatingCalculator.applyDelta(10, -30)).isZero();
        assertThat(RatingCalculator.applyDelta(50, -30)).isEqualTo(20);
    }

    @Test
    void rejectsInvalidParticipantCountOrPlacement() {
        assertThatIllegalArgumentException().isThrownBy(() -> RatingCalculator.delta(1, 1));
        assertThatIllegalArgumentException().isThrownBy(() -> RatingCalculator.delta(4, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> RatingCalculator.delta(4, 5));
    }
}
