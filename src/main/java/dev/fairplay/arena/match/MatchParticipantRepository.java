package dev.fairplay.arena.match;

import dev.fairplay.arena.match.dto.PlayerMatchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MatchParticipantRepository extends JpaRepository<MatchParticipant, MatchParticipantId> {

    @Query("""
            select mp from MatchParticipant mp
            join fetch mp.player
            where mp.match.id = :matchId
            order by mp.placement asc
            """)
    List<MatchParticipant> findByMatchIdWithPlayerOrderByPlacement(@Param("matchId") UUID matchId);

    @Query(
            value = """
                    select new dev.fairplay.arena.match.dto.PlayerMatchResponse(
                        m.id,
                        m.externalId,
                        m.gameMode,
                        m.mapName,
                        m.endedAt,
                        mp.placement,
                        cast((select count(mp2) from MatchParticipant mp2 where mp2.match = m) as int),
                        mp.kills,
                        mp.deaths,
                        mp.headshots,
                        mp.shotsFired,
                        mp.shotsHit,
                        mp.avgReactionMs,
                        mp.ratingBefore,
                        mp.ratingAfter
                    )
                    from MatchParticipant mp
                    join mp.match m
                    where mp.player.id = :playerId
                    order by m.endedAt desc, m.id desc
                    """,
            countQuery = "select count(mp) from MatchParticipant mp where mp.player.id = :playerId")
    Page<PlayerMatchResponse> findPlayerMatches(@Param("playerId") UUID playerId, Pageable pageable);
}
