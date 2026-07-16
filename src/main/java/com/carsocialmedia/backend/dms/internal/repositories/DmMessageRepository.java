package com.carsocialmedia.backend.dms.internal.repositories;

import com.carsocialmedia.backend.dms.internal.entities.DmMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DmMessageRepository extends JpaRepository<DmMessageEntity, UUID> {

    List<DmMessageEntity> findByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId, Pageable pageable);

    /** Keyset continuation of {@link #findByConversationIdOrderByCreatedAtDescIdDesc}: strictly after the cursor row. */
    @Query("""
            select m from DmMessageEntity m
            where m.conversationId = :conversationId
              and (m.createdAt < :createdAt or (m.createdAt = :createdAt and m.id < :id))
            order by m.createdAt desc, m.id desc
            """)
    List<DmMessageEntity> findPageAfter(@Param("conversationId") UUID conversationId,
                                        @Param("createdAt") Instant createdAt,
                                        @Param("id") UUID id,
                                        Pageable pageable);

    /** The read watermark target: the conversation's latest message, if any. */
    Optional<DmMessageEntity> findFirstByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId);

    /** Scoped fetch for delete: only the sender may touch a message (missing = same 404). */
    Optional<DmMessageEntity> findByIdAndSenderId(UUID id, UUID senderId);
}
