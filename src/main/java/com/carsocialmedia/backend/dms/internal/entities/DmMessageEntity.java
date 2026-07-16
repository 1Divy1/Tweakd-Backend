package com.carsocialmedia.backend.dms.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One DM. {@code createdAt} is written by the app (not the DB default) so the keyset cursor and
 * {@code dm_conversations.lastMessageAt} agree to the exact same instant. Soft delete: the row
 * stays, {@code deleted} flips and {@code content} is blanked for privacy.
 */
@Entity
@Table(name = "dm_messages")
@Getter
@Setter
public class DmMessageEntity {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
