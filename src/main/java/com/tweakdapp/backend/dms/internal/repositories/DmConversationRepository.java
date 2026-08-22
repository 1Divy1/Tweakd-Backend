package com.tweakdapp.backend.dms.internal.repositories;

import com.tweakdapp.backend.dms.internal.entities.DmConversationEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DmConversationRepository extends JpaRepository<DmConversationEntity, UUID> {

    /** The pair's conversation; callers must pass the canonical order (userA < userB). */
    Optional<DmConversationEntity> findByUserAAndUserB(UUID userA, UUID userB);

    /**
     * Race-safe find-or-create half: inserts the pair's conversation unless it already exists.
     * Native ON CONFLICT because two "first messages" can arrive simultaneously — a JPA save would
     * blow up the whole transaction on the unique violation.
     */
    @Modifying
    @Query(value = """
            insert into dm_conversations (id, user_a, user_b)
            values (:id, :userA, :userB)
            on conflict (user_a, user_b) do nothing
            """, nativeQuery = true)
    void insertIgnoringConflict(@Param("id") UUID id, @Param("userA") UUID userA, @Param("userB") UUID userB);

    /**
     * First page of the caller's chats list: conversations they participate in, have not hidden,
     * and that carry at least one message; most recently active first.
     */
    @Query("""
            select c from DmConversationEntity c
            join DmParticipantStateEntity s on s.conversationId = c.id
            where s.userId = :userId
              and s.hiddenAt is null
              and c.lastMessageAt is not null
            order by c.lastMessageAt desc, c.id desc
            """)
    List<DmConversationEntity> findFirstPage(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Everyone the user has an actual (message-carrying) conversation with — the audience for
     * their presence changes. Hidden conversations are deliberately included: a stray presence
     * push there is harmless and keeps the query index-only simple.
     */
    @Query("""
            select case when c.userA = :userId then c.userB else c.userA end
            from DmConversationEntity c
            where (c.userA = :userId or c.userB = :userId) and c.lastMessageAt is not null
            """)
    List<UUID> findPeerIdsOf(@Param("userId") UUID userId);

    /** Keyset continuation of {@link #findFirstPage}: strictly after the cursor row. */
    @Query("""
            select c from DmConversationEntity c
            join DmParticipantStateEntity s on s.conversationId = c.id
            where s.userId = :userId
              and s.hiddenAt is null
              and c.lastMessageAt is not null
              and (c.lastMessageAt < :lastMessageAt
                   or (c.lastMessageAt = :lastMessageAt and c.id < :id))
            order by c.lastMessageAt desc, c.id desc
            """)
    List<DmConversationEntity> findPageAfter(@Param("userId") UUID userId,
                                             @Param("lastMessageAt") Instant lastMessageAt,
                                             @Param("id") UUID id,
                                             Pageable pageable);
}
