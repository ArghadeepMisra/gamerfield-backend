package dev.fairplay.arena.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties("arena")
public record ArenaProperties(
        @NotNull @Valid Jwt jwt,
        @NotNull @Valid Rating rating,
        @NotNull @Valid Leaderboard leaderboard,
        @NotNull @Valid Stats stats,
        @NotNull @Valid Moderation moderation,
        @NotNull @Valid Accounts accounts
) {
    public record Jwt(
            @NotBlank @Size(min = 32) String secret,
            @NotBlank String issuer,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl
    ) {}

    public record Rating(
            @Positive int initial,
            @Positive int maxDelta
    ) {}

    public record Leaderboard(
            @NotBlank String key,
            boolean rebuildOnStartup,
            @Positive int rebuildBatchSize
    ) {}

    public record Stats(
            @Positive int minMatchesForPercentiles,
            @NotNull Duration refreshInterval
    ) {}

    public record Moderation(
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal serviceBanMinConfidence
    ) {}

    public record Accounts(
            @NotNull @Valid AccountCredential moderator,
            @NotEmpty @Valid List<AccountCredential> services
    ) {}

    public record AccountCredential(
            @NotBlank String username,
            @NotBlank String password
    ) {}
}
