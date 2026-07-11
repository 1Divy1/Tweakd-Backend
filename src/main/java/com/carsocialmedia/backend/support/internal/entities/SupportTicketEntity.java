package com.carsocialmedia.backend.support.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One support ticket. {@code userId} / {@code assignedTo} are flat {@code profiles.id} references;
 * {@code status} and {@code priority} rely on the column DEFAULTs on insert ({@code open} /
 * {@code normal}) and are advanced by the service afterwards. {@code lastMessageAt} and
 * {@code updatedAt} are maintained by a Supabase trigger on {@code support_ticket_messages}.
 */
@Entity
@Table(name = "support_tickets")
@Getter
@Setter
public class SupportTicketEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "subject", nullable = false)
    private String subject;

    /** FK to {@code support_ticket_categories.id}. */
    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "priority", insertable = false)
    private String priority;

    @Column(name = "status", insertable = false)
    private String status;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    /** Set when status moves to {@code resolved}; cleared when a reply reopens the ticket. */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** DB-managed: bumped by the message trigger. */
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    /** DB-managed: bumped by the message trigger. */
    @Column(name = "last_message_at", insertable = false, updatable = false)
    private Instant lastMessageAt;
}
