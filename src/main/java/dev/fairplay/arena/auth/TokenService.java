package dev.fairplay.arena.auth;

import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.player.Player;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class TokenService {

    public record AccessToken(String value, Instant expiresAt) {}

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final JwtEncoder jwtEncoder;
    private final ArenaProperties props;
    private final Clock clock;

    public TokenService(JwtEncoder jwtEncoder, ArenaProperties props, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.clock = clock;
    }

    public AccessToken issueAccessToken(Player player) {
        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(props.jwt().accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.jwt().issuer())
                .subject(player.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("username", player.getUsername())
                .claim("roles", List.of(player.getRole().name()))
                .claim("typ", "access")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, expiresAt);
    }

    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_URL.encodeToString(bytes);
    }

    public Instant refreshTokenExpiresAt() {
        return Instant.now(clock).plus(props.jwt().refreshTokenTtl());
    }

    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
