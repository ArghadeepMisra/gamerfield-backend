package dev.fairplay.arena.auth;

import dev.fairplay.arena.auth.dto.LoginRequest;
import dev.fairplay.arena.auth.dto.RefreshRequest;
import dev.fairplay.arena.auth.dto.RegisterRequest;
import dev.fairplay.arena.auth.dto.TokenResponse;
import dev.fairplay.arena.common.error.ApiException;
import dev.fairplay.arena.common.error.ErrorCode;
import dev.fairplay.arena.config.ArenaProperties;
import dev.fairplay.arena.leaderboard.LeaderboardEvents.RatingChanged;
import dev.fairplay.arena.moderation.AccountStatus;
import dev.fairplay.arena.player.Player;
import dev.fairplay.arena.player.PlayerRepository;
import dev.fairplay.arena.player.PlayerService;
import dev.fairplay.arena.player.Role;
import dev.fairplay.arena.player.dto.PlayerDetailResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final PlayerRepository playerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final PlayerService playerService;
    private final ApplicationEventPublisher eventPublisher;
    private final ArenaProperties props;
    private final Clock clock;
    private final TransactionTemplate requiresNewTx;

    public AuthService(
            PlayerRepository playerRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            PlayerService playerService,
            ApplicationEventPublisher eventPublisher,
            ArenaProperties props,
            Clock clock,
            PlatformTransactionManager txManager) {
        this.playerRepository = playerRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.playerService = playerService;
        this.eventPublisher = eventPublisher;
        this.props = props;
        this.clock = clock;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public PlayerDetailResponse register(RegisterRequest request) {
        if (playerRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN, "Username is already taken");
        }
        if (playerRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN, "Email is already taken");
        }
        Instant now = Instant.now(clock);
        Player player = new Player(
                UUID.randomUUID(),
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                Role.PLAYER,
                AccountStatus.ACTIVE,
                request.displayName(),
                request.country(),
                props.rating().initial(),
                now);
        playerRepository.saveAndFlush(player);
        eventPublisher.publishEvent(new RatingChanged(player.getId(), player.getRating()));
        return playerService.toDetail(player, true);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Player player = playerRepository.findByUsernameIgnoreCase(request.username())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password"));
        if (!passwordEncoder.matches(request.password(), player.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password");
        }
        return issueTokenPair(player);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String hash = tokenService.hashRefreshToken(request.refreshToken());
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Invalid refresh token"));

        Instant now = Instant.now(clock);
        // Expiry is checked before reuse: an expired token is a plain INVALID_REFRESH_TOKEN and must
        // not trigger the mass revocation that a replayed (revoked but unexpired) token does.
        if (!existing.getExpiresAt().isAfter(now)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token expired");
        }
        if (existing.getRevokedAt() != null) {
            UUID playerId = existing.getPlayer().getId();
            requiresNewTx.executeWithoutResult(status ->
                    refreshTokenRepository.revokeAllActiveForPlayer(playerId, now));
            throw new ApiException(ErrorCode.REFRESH_TOKEN_REUSED, "Refresh token was already used");
        }

        UUID replacementId = UUID.randomUUID();
        String newRawRefresh = tokenService.generateRefreshToken();
        Instant refreshExpiresAt = tokenService.refreshTokenExpiresAt();
        RefreshToken replacement = new RefreshToken(
                replacementId,
                existing.getPlayer(),
                tokenService.hashRefreshToken(newRawRefresh),
                refreshExpiresAt,
                now);
        refreshTokenRepository.save(replacement);

        existing.setRevokedAt(now);
        existing.setReplacedBy(replacementId);

        TokenService.AccessToken accessToken = tokenService.issueAccessToken(existing.getPlayer());
        return new TokenResponse(
                "Bearer",
                accessToken.value(),
                accessToken.expiresAt(),
                newRawRefresh,
                refreshExpiresAt);
    }

    private TokenResponse issueTokenPair(Player player) {
        Instant now = Instant.now(clock);
        String rawRefresh = tokenService.generateRefreshToken();
        Instant refreshExpiresAt = tokenService.refreshTokenExpiresAt();
        RefreshToken refreshToken = new RefreshToken(
                UUID.randomUUID(),
                player,
                tokenService.hashRefreshToken(rawRefresh),
                refreshExpiresAt,
                now);
        refreshTokenRepository.save(refreshToken);

        TokenService.AccessToken accessToken = tokenService.issueAccessToken(player);
        return new TokenResponse(
                "Bearer",
                accessToken.value(),
                accessToken.expiresAt(),
                rawRefresh,
                refreshExpiresAt);
    }
}
