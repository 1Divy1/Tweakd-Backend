package com.tweakdapp.backend.notification.internal.repositories;

import com.tweakdapp.backend.notification.internal.entities.NotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {

    List<NotificationEntity> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, Pageable pageable);

    /** Keyset continuation of {@link #findByUserIdOrderByCreatedAtDescIdDesc}: strictly after the cursor row. */
    @Query("""
            select n from NotificationEntity n
            where n.userId = :userId
              and (n.createdAt < :createdAt or (n.createdAt = :createdAt and n.id < :id))
            order by n.createdAt desc, n.id desc
            """)
    List<NotificationEntity> findPageAfter(@Param("userId") UUID userId,
                                           @Param("createdAt") Instant createdAt,
                                           @Param("id") UUID id,
                                           Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    /**
     * Unread counts for many users in one round trip, as {@code [userId, count]} rows. Used to build
     * the per-recipient push badge for a whole batch without issuing a count per notification.
     *
     * <p>Users with no unread rows are simply absent from the result — callers default them to 0.
     */
    @Query("""
            select n.userId, count(n)
              from NotificationEntity n
             where n.userId in :userIds and n.read = false
             group by n.userId
            """)
    List<Object[]> countUnreadGrouped(@Param("userIds") Collection<UUID> userIds);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update NotificationEntity n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") UUID userId);

    /**
     * Deletes the notifications either user received about the other — every row whose recorded
     * {@code actor_id} is the other side. Run when one of them blocks the other.
     */
    @Modifying
    @Query(value = """
            delete from notifications
             where (user_id = :first and payload ->> 'actor_id' = cast(:second as text))
                or (user_id = :second and payload ->> 'actor_id' = cast(:first as text))
            """, nativeQuery = true)
    int deleteBetween(@Param("first") UUID first, @Param("second") UUID second);
}
