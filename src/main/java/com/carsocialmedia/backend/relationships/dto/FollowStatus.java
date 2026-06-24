package com.carsocialmedia.backend.relationships.dto;

/**
 * Relationship status from the perspective of the current user toward a target user.
 *
 * <ul>
 *   <li>{@link #NOT_FOLLOWING} — no relationship row exists</li>
 *   <li>{@link #PENDING}       — request sent, awaiting approval (target is private)</li>
 *   <li>{@link #ACCEPTED}      — actively following</li>
 * </ul>
 */
public enum FollowStatus {
    NOT_FOLLOWING,
    PENDING,
    ACCEPTED
}
