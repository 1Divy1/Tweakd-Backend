package com.tweakdapp.backend.dms.internal.repositories;

import com.tweakdapp.backend.dms.internal.entities.DmParticipantStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DmParticipantStateRepository
        extends JpaRepository<DmParticipantStateEntity, DmParticipantStateEntity.Key> {

    /** Also the participant check: empty means "not your conversation" (rendered as 404). */
    Optional<DmParticipantStateEntity> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    /** Batch-load the caller's states for one chats-list page (unread counts). */
    List<DmParticipantStateEntity> findByConversationIdInAndUserId(Collection<UUID> conversationIds, UUID userId);

    /**
     * Companion of {@code DmConversationRepository.insertIgnoringConflict}: creates both
     * participants' state rows if this really was the pair's first message.
     */
    @Modifying
    @Query(value = """
            insert into dm_participant_state (conversation_id, user_id)
            values (:conversationId, :userA), (:conversationId, :userB)
            on conflict (conversation_id, user_id) do nothing
            """, nativeQuery = true)
    void insertIgnoringConflict(@Param("conversationId") UUID conversationId,
                                @Param("userA") UUID userA,
                                @Param("userB") UUID userB);

    /**
     * A new message for {@code userId}: bump the badge and resurrect the conversation if they had
     * hidden it.
     */
    @Modifying
    @Query("""
            update DmParticipantStateEntity s
            set s.unreadCount = s.unreadCount + 1, s.hiddenAt = null
            where s.conversationId = :conversationId and s.userId = :userId
            """)
    void registerIncomingMessage(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    /** Sending also resurrects the conversation on the sender's own side, sans unread bump. */
    @Modifying
    @Query("""
            update DmParticipantStateEntity s
            set s.hiddenAt = null
            where s.conversationId = :conversationId and s.userId = :userId and s.hiddenAt is not null
            """)
    void unhide(@Param("conversationId") UUID conversationId, @Param("userId") UUID userId);

    /** Total unread DMs for the app badge. */
    @Query("select coalesce(sum(s.unreadCount), 0) from DmParticipantStateEntity s where s.userId = :userId")
    long sumUnread(@Param("userId") UUID userId);
}
