package com.carsocialmedia.backend.admin.internal;

/**
 * What a dashboard endpoint may require, mapped to roles by {@link AdminRole}. Feedback
 * administration (respond, change status) rides on {@link #ANSWER_TICKETS} — it is user
 * communication, the support agents' job.
 */
public enum Capability {
    /** Moderation queue: view cases, approve, remove content, escalate. */
    REVIEW_CONTENT,
    /** Warn, ban, and unban authors. */
    WARN_BAN,
    /** Support tickets and the feedback admin operations. */
    ANSWER_TICKETS,
    /** Process refunds (capability flag only — no billing integration yet). */
    REFUNDS,
    /** The overview / analytics pages. */
    VIEW_ANALYTICS,
    /** Add, re-role, and remove team members. */
    MANAGE_TEAM,
    /** Hand the owner role to someone else (owner only; endpoint TODO). */
    TRANSFER_OWNERSHIP
}
