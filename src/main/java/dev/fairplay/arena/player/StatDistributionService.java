package dev.fairplay.arena.player;

import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.player.dto.PlayerPercentiles;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class StatDistributionService {

    public record Distribution(double[] headshotRate, double[] accuracy, double[] reactionMs) {}

    private static final double[] PERCENTILE_POINTS = buildPercentilePoints();
    private static final String PERCENTILE_SQL_TEMPLATE = """
            SELECT percentile_cont(:points) WITHIN GROUP (ORDER BY %s)
            FROM players
            WHERE role = 'PLAYER'
              AND status NOT IN ('BANNED', 'APPEALED')
              AND matches_played >= :minMatches
            """;

    private final JdbcClient jdbcClient;
    private final int minMatchesForPercentiles;
    private final AtomicReference<Distribution> current = new AtomicReference<>();

    public StatDistributionService(JdbcClient jdbcClient, ArenaProperties props) {
        this.jdbcClient = jdbcClient;
        this.minMatchesForPercentiles = props.stats().minMatchesForPercentiles();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${arena.stats.refresh-interval}")
    public void refresh() {
        double[] hs = queryPercentiles(
                "CASE WHEN total_kills = 0 THEN 0.0 ELSE total_headshots::double precision / total_kills END");
        double[] acc = queryPercentiles(
                "CASE WHEN total_shots_fired = 0 THEN 0.0 ELSE total_shots_hit::double precision / total_shots_fired END");
        double[] react = queryPercentiles(
                "sum_reaction_ms::double precision / matches_played");

        if (hs == null || acc == null || react == null) {
            current.set(null);
            return;
        }
        current.set(new Distribution(hs, acc, react));
    }

    public PlayerPercentiles percentilesFor(
            int matchesPlayed,
            double headshotRate,
            double accuracy,
            Integer avgReactionMs) {
        Distribution dist = current.get();
        if (dist == null || matchesPlayed < minMatchesForPercentiles || avgReactionMs == null) {
            return null;
        }
        int hsPct = percentile(dist.headshotRate(), headshotRate);
        int accPct = percentile(dist.accuracy(), accuracy);
        int reactionSpeed = 100 - percentile(dist.reactionMs(), avgReactionMs.doubleValue());
        return new PlayerPercentiles(hsPct, accPct, reactionSpeed);
    }

    public void setDistributionForTest(Distribution distribution) {
        current.set(distribution);
    }

    static int percentile(double[] cutPoints, double value) {
        int lo = 0;
        int hi = cutPoints.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cutPoints[mid] <= value) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return Math.clamp(lo - 1, 0, 100);
    }

    private double[] queryPercentiles(String orderExpr) {
        String sql = PERCENTILE_SQL_TEMPLATE.formatted(orderExpr);
        // percentile_cont returns NULL when no player is eligible (for example an empty database at first
        // startup). JdbcClient rejects a null ResultSetExtractor result, which would crash the app on
        // ApplicationReadyEvent, so the extractor returns an Optional and "no distribution yet" is
        // represented by null percentiles.
        return jdbcClient.sql(sql)
                .param("points", PERCENTILE_POINTS)
                .param("minMatches", minMatchesForPercentiles)
                .query(rs -> {
                    if (!rs.next()) {
                        return Optional.<double[]>empty();
                    }
                    Array sqlArray = rs.getArray(1);
                    if (sqlArray == null) {
                        return Optional.<double[]>empty();
                    }
                    try {
                        Object raw = sqlArray.getArray();
                        if (!(raw instanceof Object[] elements) || elements.length == 0) {
                            return Optional.<double[]>empty();
                        }
                        double[] result = new double[elements.length];
                        for (int i = 0; i < elements.length; i++) {
                            if (elements[i] == null) {
                                return Optional.<double[]>empty();
                            }
                            result[i] = ((Number) elements[i]).doubleValue();
                        }
                        return Optional.of(result);
                    } finally {
                        try {
                            sqlArray.free();
                        } catch (SQLException ignored) {
                            // Ignore driver cleanup failure
                        }
                    }
                })
                .orElse(null);
    }

    private static double[] buildPercentilePoints() {
        double[] points = new double[101];
        for (int i = 0; i <= 100; i++) {
            points[i] = i / 100.0;
        }
        return points;
    }
}
