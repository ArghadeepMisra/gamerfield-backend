package dev.fairplay.arena.support;

import dev.fairplay.arena.auth.TokenService;
import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.match.GameMode;
import dev.fairplay.arena.match.MatchService;
import dev.fairplay.arena.match.dto.MatchResponse;
import dev.fairplay.arena.match.dto.ParticipantRequest;
import dev.fairplay.arena.match.dto.RecordMatchRequest;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.player.PlayerRepository;
import dev.fairplay.arena.player.Role;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class TestDataFactory {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final MatchService matchService;
    private final StringRedisTemplate redisTemplate;
    private final ArenaProperties props;
    private final Clock clock;

    public TestDataFactory(
            PlayerRepository playerRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            MatchService matchService,
            StringRedisTemplate redisTemplate,
            ArenaProperties props,
            Clock clock) {
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.matchService = matchService;
        this.redisTemplate = redisTemplate;
        this.props = props;
        this.clock = clock;
    }

    public Player player(String username) {
        return player(username, AccountStatus.ACTIVE, props.rating().initial());
    }

    public Player player(String username, AccountStatus status, int rating) {
        Instant now = Instant.now(clock);
        Player p = new Player(
                UUID.randomUUID(),
                username,
                username + "@example.com",
                passwordEncoder.encode("password123"),
                Role.PLAYER,
                status,
                username,
                "US",
                rating,
                now);
        Player saved = playerRepository.saveAndFlush(p);
        if (saved.isEligibleForMatchAndRanking()) {
            redisTemplate.opsForZSet().add(props.leaderboard().key(), saved.getId().toString(), rating);
        }
        return saved;
    }

    public Player moderator() {
        return moderator("mod-admin");
    }

    public Player moderator(String username) {
        Instant now = Instant.now(clock);
        Player mod = new Player(
                UUID.randomUUID(),
                username,
                username + "@fairplay.local",
                passwordEncoder.encode("test"),
                Role.MODERATOR,
                AccountStatus.ACTIVE,
                null,
                null,
                0,
                now);
        return playerRepository.saveAndFlush(mod);
    }

    public Player service(String username) {
        Instant now = Instant.now(clock);
        Player svc = new Player(
                UUID.randomUUID(),
                username,
                username + "@fairplay.local",
                passwordEncoder.encode("test"),
                Role.SERVICE,
                AccountStatus.ACTIVE,
                null,
                null,
                0,
                now);
        return playerRepository.saveAndFlush(svc);
    }

    public String bearer(Player player) {
        return "Bearer " + tokenService.issueAccessToken(player).value();
    }

    public MatchResponse match(Player... players) {
        Player recorder = playerRepository.findByUsernameIgnoreCase("telemetry-sim")
                .orElseGet(() -> service("telemetry-sim"));
        Instant now = Instant.now(clock);
        List<ParticipantRequest> participants = new ArrayList<>(players.length);
        for (int i = 0; i < players.length; i++) {
            participants.add(new ParticipantRequest(
                    players[i].getId(),
                    i + 1,
                    10 - i,
                    2 + i,
                    4,
                    100,
                    35,
                    220 + i * 10));
        }
        RecordMatchRequest req = new RecordMatchRequest(
                "match-" + UUID.randomUUID(),
                GameMode.FFA,
                "Rust Yard",
                now.minusSeconds(600),
                now,
                participants);
        return matchService.recordMatch(req, recorder.getId()).match();
    }
}
