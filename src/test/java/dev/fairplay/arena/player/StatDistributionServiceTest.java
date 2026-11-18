package dev.fairplay.arena.player;

import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.player.dto.PlayerPercentiles;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StatDistributionServiceTest {

    private static StatDistributionService newService() {
        ArenaProperties props = new ArenaProperties(
                new ArenaProperties.Jwt("test-secret-that-is-at-least-32-chars-long", "fairplay-arena",
                        Duration.ofMinutes(15), Duration.ofDays(7)),
                new ArenaProperties.Rating(1000, 30),
                new ArenaProperties.Leaderboard("leaderboard:global", true, 10000),
                new ArenaProperties.Stats(5, Duration.ofSeconds(60)),
                new ArenaProperties.Moderation(new BigDecimal("0.80")),
                new ArenaProperties.Accounts(
                        new ArenaProperties.AccountCredential("mod-admin", "test"),
                        List.of(new ArenaProperties.AccountCredential("telemetry-sim", "test"))));
        return new StatDistributionService(mock(JdbcClient.class), props);
    }

    @Test
    void returnsNullWhenUnderMinMatchesOrDistributionNotReady() {
        StatDistributionService service = newService();
        assertThat(service.percentilesFor(10, 0.5, 0.3, 200)).isNull();

        double[] linear = linearCuts(0.0, 1.0);
        service.setDistributionForTest(new StatDistributionService.Distribution(linear, linear, linearCuts(100.0, 100.0)));
        assertThat(service.percentilesFor(4, 0.5, 0.3, 200)).isNull();
    }

    @Test
    void looksUpEdgesBetweenCutPointsAndInvertsReactionSpeed() {
        StatDistributionService service = newService();
        double[] hsCuts = linearCuts(0.0, 0.01);       // 0.00 .. 1.00
        double[] accCuts = linearCuts(0.10, 0.005);    // 0.10 .. 0.60
        double[] reactCuts = linearCuts(100.0, 2.0);   // 100 .. 300 ms
        service.setDistributionForTest(new StatDistributionService.Distribution(hsCuts, accCuts, reactCuts));

        // At minimum edge (and below minimum)
        PlayerPercentiles minEdge = service.percentilesFor(5, 0.0, 0.05, 90);
        assertThat(minEdge).isEqualTo(new PlayerPercentiles(0, 0, 100));

        // Between cut points: 0.504 -> 51 cuts <= 0.504 -> index 50; 0.352 -> 51 cuts <= 0.352 -> index 50; 201ms -> 51 cuts <= 201 -> index 50 -> speed 50
        PlayerPercentiles mid = service.percentilesFor(10, 0.504, 0.352, 201);
        assertThat(mid).isEqualTo(new PlayerPercentiles(50, 50, 50));

        // At maximum edge (and above maximum)
        PlayerPercentiles maxEdge = service.percentilesFor(10, 1.5, 0.60, 350);
        assertThat(maxEdge).isEqualTo(new PlayerPercentiles(100, 100, 0));
    }

    private static double[] linearCuts(double start, double step) {
        double[] cuts = new double[101];
        for (int i = 0; i <= 100; i++) {
            cuts[i] = start + i * step;
        }
        return cuts;
    }
}
