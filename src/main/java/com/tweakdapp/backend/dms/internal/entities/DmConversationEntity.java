package com.tweakdapp.backend.dms.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One 1:1 conversation. The pair is stored in canonical order ({@code user_a < user_b}, enforced by
 * a DB CHECK + UNIQUE) so a pair maps to exactly one row. The {@code lastMessage*} trio is
 * denormalized here so the chats list never touches {@code dm_messages}.
 */
@Entity
@Table(name = "dm_conversations")
@Getter
@Setter
public class DmConversationEntity {

    @Id
    private UUID id;

    @Column(name = "user_a", nullable = false, updatable = false)
    private UUID userA;

    @Column(name = "user_b", nullable = false, updatable = false)
    private UUID userB;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    /** Truncated content of the latest message; {@code null} when that message was deleted. */
    @Column(name = "last_message_preview")
    private String lastMessagePreview;

    @Column(name = "last_message_sender_id")
    private UUID lastMessageSenderId;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** The other participant, from {@code userId}'s point of view. */
    public UUID peerOf(UUID userId) {
        return userA.equals(userId) ? userB : userA;
    }
}
