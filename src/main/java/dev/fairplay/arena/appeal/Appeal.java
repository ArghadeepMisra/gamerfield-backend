package dev.fairplay.arena.appeal;

import dev.fairplay.arena.moderation.ModerationAction;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appeals")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Appeal {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ban_action_id", nullable = false, unique = true)
    private ModerationAction banAction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AppealStatus status;

    @Column(nullable = false, length = 2000)
    private String statement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private Player decidedBy;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public Appeal(
            UUID id,
            Player player,
            ModerationAction banAction,
            AppealStatus status,
            String statement,
            Instant createdAt) {
        this.id = id;
        this.player = player;
        this.banAction = banAction;
        this.status = status;
        this.statement = statement;
        this.createdAt = createdAt;
    }
}
