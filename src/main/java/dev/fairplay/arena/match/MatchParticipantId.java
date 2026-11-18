package dev.fairplay.arena.match;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Getter
public class MatchParticipantId implements Serializable {

    @Column(name = "match_id")
    private UUID matchId;

    @Column(name = "player_id")
    private UUID playerId;

    protected MatchParticipantId() {}

    public MatchParticipantId(UUID matchId, UUID playerId) {
        this.matchId = matchId;
        this.playerId = playerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchParticipantId that)) return false;
        return Objects.equals(matchId, that.matchId) && Objects.equals(playerId, that.playerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(matchId, playerId);
    }
}
