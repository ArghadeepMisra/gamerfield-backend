package dev.fairplay.arena.match;

import dev.fairplay.arena.player.Player;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Match {

    @Id
    private UUID id;

    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_mode", nullable = false, length = 16)
    private GameMode gameMode;

    @Column(name = "map_name", nullable = false, length = 40)
    private String mapName;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private Player recordedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Match(
            UUID id,
            String externalId,
            GameMode gameMode,
            String mapName,
            Instant startedAt,
            Instant endedAt,
            Player recordedBy,
            Instant createdAt) {
        this.id = id;
        this.externalId = externalId;
        this.gameMode = gameMode;
        this.mapName = mapName;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.recordedBy = recordedBy;
        this.createdAt = createdAt;
    }
}
