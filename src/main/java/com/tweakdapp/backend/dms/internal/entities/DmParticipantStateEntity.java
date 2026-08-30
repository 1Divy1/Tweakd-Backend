package com.tweakdapp.backend.dms.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * One participant's view of a conversation: unread badge count, read watermark (the receipt the
 * peer renders as "read" ticks), and {@code hiddenAt} — "delete chat" hides the conversation for
 * this side only; any new message unhides it. Two rows per conversation, created with it.
 */
@Entity
@Table(name = "dm_participant_state")
@IdClass(DmParticipantStateEntity.Key.class)
@Getter
@Setter
public class DmParticipantStateEntity {

    @Id
    @Column(name = "conversation_id")
    private UUID conversationId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "unread_count", nullable = false)
    private int unreadCount;

    @Column(name = "last_read_message_id")
    private UUID lastReadMessageId;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    public record Key(UUID conversationId, UUID userId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
