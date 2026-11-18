package dev.fairplay.arena.match;

import dev.fairplay.arena.player.Player;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "match_participants")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchParticipant {

    @EmbeddedId
    private MatchParticipantId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("matchId")
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("playerId")
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(nullable = false)
    private int placement;

    @Column(nullable = false)
    private int kills;

    @Column(nullable = false)
    private int deaths;

    @Column(nullable = false)
    private int headshots;

    @Column(name = "shots_fired", nullable = false)
    private int shotsFired;

    @Column(name = "shots_hit", nullable = false)
    private int shotsHit;

    @Column(name = "avg_reaction_ms", nullable = false)
    private int avgReactionMs;

    @Column(name = "rating_before", nullable = false)
    private int ratingBefore;

    @Column(name = "rating_after", nullable = false)
    private int ratingAfter;

    public MatchParticipant(
            Match match,
            Player player,
            int placement,
            int kills,
            int deaths,
            int headshots,
            int shotsFired,
            int shotsHit,
            int avgReactionMs,
            int ratingBefore,
            int ratingAfter) {
        this.id = new MatchParticipantId(match.getId(), player.getId());
        this.match = match;
        this.player = player;
        this.placement = placement;
        this.kills = kills;
        this.deaths = deaths;
        this.headshots = headshots;
        this.shotsFired = shotsFired;
        this.shotsHit = shotsHit;
        this.avgReactionMs = avgReactionMs;
        this.ratingBefore = ratingBefore;
        this.ratingAfter = ratingAfter;
    }
}
