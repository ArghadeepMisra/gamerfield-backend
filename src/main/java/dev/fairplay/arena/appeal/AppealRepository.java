package dev.fairplay.arena.appeal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppealRepository extends JpaRepository<Appeal, UUID> {

    boolean existsByBanActionId(UUID banActionId);

    @Query("""
            select a from Appeal a
            join fetch a.player
            left join fetch a.decidedBy
            where a.id = :id
            """)
    Optional<Appeal> findByIdWithDetails(@Param("id") UUID id);

    @Query(
            value = """
                    select a from Appeal a
                    join fetch a.player
                    left join fetch a.decidedBy
                    where a.status = :status
                    order by a.createdAt asc
                    """,
            countQuery = "select count(a) from Appeal a where a.status = :status")
    Page<Appeal> findByStatusOrderByCreatedAtAsc(@Param("status") AppealStatus status, Pageable pageable);
}
