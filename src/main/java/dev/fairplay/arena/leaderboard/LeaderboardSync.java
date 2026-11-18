package dev.fairplay.arena.leaderboard;

import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.leaderboard.LeaderboardEvents.RatingChanged;
import dev.fairplay.arena.leaderboard.LeaderboardEvents.RemovedFromLeaderboard;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class LeaderboardSync {

    private final StringRedisTemplate redisTemplate;
    private final String leaderboardKey;
    private final Counter syncFailures;

    public LeaderboardSync(
            StringRedisTemplate redisTemplate,
            ArenaProperties props,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.leaderboardKey = props.leaderboard().key();
        this.syncFailures = Counter.builder("arena.leaderboard.sync.failures")
                .description("Number of failed post-commit Redis leaderboard sync operations")
                .register(meterRegistry);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRatingChanged(RatingChanged event) {
        try {
            redisTemplate.opsForZSet().add(
                    leaderboardKey,
                    event.playerId().toString(),
                    event.rating());
        } catch (Exception ex) {
            syncFailures.increment();
            log.warn("Failed to sync rating change to Redis for player {}", event.playerId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRemovedFromLeaderboard(RemovedFromLeaderboard event) {
        try {
            redisTemplate.opsForZSet().remove(leaderboardKey, event.playerId().toString());
        } catch (Exception ex) {
            syncFailures.increment();
            log.warn("Failed to remove player {} from Redis leaderboard", event.playerId(), ex);
        }
    }
}
