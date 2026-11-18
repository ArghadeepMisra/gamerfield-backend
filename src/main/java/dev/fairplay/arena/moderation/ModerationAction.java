package dev.fairplay.arena.moderation;

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
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_actions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModerationAction {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ModerationActionType action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 16)
    private AccountStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    private AccountStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private Player actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ActionSource source;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "evidence", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String evidenceJson;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(name = "requires_human_review", nullable = false)
    private boolean requiresHumanReview;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ModerationAction(
            UUID id,
            Player player,
            ModerationActionType action,
            AccountStatus fromStatus,
            AccountStatus toStatus,
            Player actor,
            ActionSource source,
            String reason,
            String evidenceJson,
            BigDecimal confidence,
            boolean requiresHumanReview,
            Instant createdAt) {
        this.id = id;
        this.player = player;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.source = source;
        this.reason = reason;
        this.evidenceJson = evidenceJson;
        this.confidence = confidence;
        this.requiresHumanReview = requiresHumanReview;
        this.createdAt = createdAt;
    }
}
