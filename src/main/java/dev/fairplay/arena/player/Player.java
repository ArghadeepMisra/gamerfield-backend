package dev.fairplay.arena.player;

import dev.fairplay.arena.moderation.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "players")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Player {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "display_name", length = 40)
    private String displayName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 2)
    private String country;

    @Column(nullable = false)
    private int rating;

    @Column(name = "matches_played", nullable = false)
    private int matchesPlayed;

    @Column(name = "total_kills", nullable = false)
    private long totalKills;

    @Column(name = "total_deaths", nullable = false)
    private long totalDeaths;

    @Column(name = "total_headshots", nullable = false)
    private long totalHeadshots;

    @Column(name = "total_shots_fired", nullable = false)
    private long totalShotsFired;

    @Column(name = "total_shots_hit", nullable = false)
    private long totalShotsHit;

    @Column(name = "sum_reaction_ms", nullable = false)
    private long sumReactionMs;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Player(
            UUID id,
            String username,
            String email,
            String passwordHash,
            Role role,
            AccountStatus status,
            String displayName,
            String country,
            int rating,
            Instant now) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.displayName = displayName;
        this.country = country;
        this.rating = rating;
        this.statusChangedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public boolean isEligibleForMatchAndRanking() {
        return role == Role.PLAYER
                && status != AccountStatus.BANNED
                && status != AccountStatus.APPEALED;
    }
}
