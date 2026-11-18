package dev.fairplay.arena.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "arena.jwt.secret=test-secret-that-is-at-least-32-chars-long",
        "arena.accounts.moderator.password=test",
        // A list set by a higher-priority property source replaces the whole list from application.yml
        // (Boot does not merge lists), so the usernames must be repeated alongside the passwords.
        "arena.accounts.services[0].username=telemetry-sim",
        "arena.accounts.services[0].password=test",
        "arena.accounts.services[1].username=judge-agent",
        "arena.accounts.services[1].password=test",
        "arena.leaderboard.rebuild-on-startup=false"
})
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected TestDataFactory data;

    @BeforeEach
    void resetState() {
        jdbc.execute("TRUNCATE appeals, moderation_actions, match_participants, matches, refresh_tokens, players CASCADE");
        redisTemplate.execute((RedisCallback<Void>) c -> {
            c.serverCommands().flushAll();
            return null;
        });
    }
}
