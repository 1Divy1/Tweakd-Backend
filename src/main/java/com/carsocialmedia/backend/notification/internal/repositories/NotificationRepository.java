package com.carsocialmedia.backend.notification.internal.repositories;

import com.carsocialmedia.backend.notification.internal.entities.NotificationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query("update NotificationEntity n set n.read = true where n.userId = :userId and n.read = false")
    int markAllRead(@Param("userId") UUID userId);
}
