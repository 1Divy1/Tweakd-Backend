package com.tweakdapp.backend.support.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One message in a ticket's conversation. {@code staff} marks replies written from the dashboard —
 * for those, {@code senderId} is a staff UUID ({@code admin_team_members.user_id}), not a profile.
 */
@Entity
@Table(name = "support_ticket_messages")
@Getter
@Setter
public class SupportTicketMessageEntity {

    @Id
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(name = "is_staff", nullable = false)
    private boolean staff;

    @Column(name = "content", nullable = false)
    private String content;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
