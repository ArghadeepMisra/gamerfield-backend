package dev.fairplay.arena.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.player.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-chars-long";
    private static final Instant FIXED_NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    void issuesAccessTokenWithExpectedClaimsHeaderAndExpiryFromClock() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

        // The token is issued with a fixed clock, so the decoder must check expiry against the same
        // clock. Otherwise the test only passes within 15 minutes of FIXED_NOW (the default
        // validator uses the real time).
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(clock);
        decoder.setJwtValidator(timestampValidator);

        ArenaProperties props = new ArenaProperties(
                new ArenaProperties.Jwt(SECRET, "fairplay-arena", Duration.ofMinutes(15), Duration.ofDays(7)),
                new ArenaProperties.Rating(1000, 30),
                new ArenaProperties.Leaderboard("leaderboard:global", true, 10000),
                new ArenaProperties.Stats(5, Duration.ofSeconds(60)),
                new ArenaProperties.Moderation(new BigDecimal("0.80")),
                new ArenaProperties.Accounts(
                        new ArenaProperties.AccountCredential("mod-admin", "test"),
                        List.of(new ArenaProperties.AccountCredential("telemetry-sim", "test"))));

        TokenService tokenService = new TokenService(encoder, props, clock);

        UUID playerId = UUID.randomUUID();
        Player player = new Player(
                playerId,
                "neo_42",
                "neo@example.com",
                "hash",
                Role.PLAYER,
                AccountStatus.ACTIVE,
                "Neo",
                "IN",
                1000,
                FIXED_NOW);

        TokenService.AccessToken issued = tokenService.issueAccessToken(player);
        assertThat(issued.expiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofMinutes(15)));

        Jwt decoded = decoder.decode(issued.value());
        assertThat(decoded.getHeaders()).containsEntry("alg", "HS256");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("fairplay-arena");
        assertThat(decoded.getSubject()).isEqualTo(playerId.toString());
        assertThat(decoded.getIssuedAt()).isEqualTo(FIXED_NOW);
        assertThat(decoded.getExpiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofMinutes(15)));
        assertThat(decoded.getClaimAsString("username")).isEqualTo("neo_42");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("PLAYER");
        assertThat(decoded.getClaimAsString("typ")).isEqualTo("access");
    }

    @Test
    void generates43CharBase64UrlRefreshTokenAndSha256HexHash() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        ArenaProperties props = new ArenaProperties(
                new ArenaProperties.Jwt(SECRET, "fairplay-arena", Duration.ofMinutes(15), Duration.ofDays(7)),
                new ArenaProperties.Rating(1000, 30),
                new ArenaProperties.Leaderboard("leaderboard:global", true, 10000),
                new ArenaProperties.Stats(5, Duration.ofSeconds(60)),
                new ArenaProperties.Moderation(new BigDecimal("0.80")),
                new ArenaProperties.Accounts(
                        new ArenaProperties.AccountCredential("mod-admin", "test"),
                        List.of(new ArenaProperties.AccountCredential("telemetry-sim", "test"))));

        TokenService tokenService = new TokenService(encoder, props, Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        String refreshToken = tokenService.generateRefreshToken();
        assertThat(refreshToken).matches("^[A-Za-z0-9_-]{43}$");

        String hash = tokenService.hashRefreshToken(refreshToken);
        assertThat(hash).matches("^[0-9a-f]{64}$");
        assertThat(tokenService.refreshTokenExpiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofDays(7)));
    }
}
