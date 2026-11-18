package dev.fairplay.arena.player;

import dev.fairplay.arena.moderation.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    @Query("select p from Player p where lower(p.username) = lower(:username)")
    Optional<Player> findByUsernameIgnoreCase(@Param("username") String username);

    @Query("select case when count(p) > 0 then true else false end from Player p where lower(p.username) = lower(:username)")
    boolean existsByUsernameIgnoreCase(@Param("username") String username);

    @Query("select case when count(p) > 0 then true else false end from Player p where lower(p.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Player p where p.id in :ids order by p.id")
    List<Player> findAllByIdInForUpdate(@Param("ids") Collection<UUID> ids);

    @Query("""
            select p from Player p
            where p.role = dev.fairplay.arena.player.Role.PLAYER
              and (:status is null or p.status = :status)
              and (:prefix is null or lower(p.username) like concat(:prefix, '%') escape '!')
            order by p.rating desc, p.id asc
            """)
    Page<Player> searchPlayers(
            @Param("prefix") String escapedLowerPrefix,
            @Param("status") AccountStatus status,
            Pageable pageable);

    @Query("""
            select count(p) from Player p
            where p.role = dev.fairplay.arena.player.Role.PLAYER
              and p.status in :statuses
            """)
    long countEligibleForLeaderboard(@Param("statuses") Collection<AccountStatus> statuses);
}
