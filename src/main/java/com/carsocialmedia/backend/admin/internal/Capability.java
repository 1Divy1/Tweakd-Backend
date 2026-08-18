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
    /**
     * Approve, reject and delete user-submitted map events. Deliberately separate from
     * {@link #REVIEW_CONTENT}: putting an event on the map is a publishing decision, not a
     * moderation one, and is reserved for owners and senior admins.
     */
    APPROVE_EVENTS,
    /** Support tickets and the feedback admin operations. */
    ANSWER_TICKETS,
    /**
     * Manage the community feedback feed: set a request's roadmap status, write the team's official
     * response, remove spam. Deliberately separate from {@link #ANSWER_TICKETS}: telling the
     * community that something is in development or shipped is a product commitment, not user
     * support, so it stays with owners and senior admins — the same reasoning as
     * {@link #APPROVE_EVENTS}.
     */
    MANAGE_ROADMAP,
    /** Process refunds (capability flag only — no billing integration yet). */
    REFUNDS,
    /** The overview / analytics pages. */
    VIEW_ANALYTICS,
    /** Add, re-role, and remove team members. */
    MANAGE_TEAM,
    /** Hand the owner role to someone else (owner only; endpoint TODO). */
    TRANSFER_OWNERSHIP
}
