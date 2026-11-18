package dev.fairplay.arena.moderation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, UUID> {

    @Query(
            value = """
                    select m from ModerationAction m
                    join fetch m.actor
                    where m.player.id = :playerId
                    order by m.createdAt desc
                    """,
            countQuery = "select count(m) from ModerationAction m where m.player.id = :playerId")
    Page<ModerationAction> findHistoryByPlayerId(@Param("playerId") UUID playerId, Pageable pageable);

    Optional<ModerationAction> findFirstByPlayerIdAndActionOrderByCreatedAtDesc(
            UUID playerId,
            ModerationActionType action);
}
