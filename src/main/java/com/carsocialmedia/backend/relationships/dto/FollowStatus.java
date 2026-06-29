package com.carsocialmedia.backend.relationships.dto;

/**
 * Relationship status from the perspective of the current user toward a target user. All accounts
 * are public, so a follow is either absent or active — there is no pending/approval state.
 *
 * <ul>
 *   <li>{@link #NOT_FOLLOWING} — no relationship row exists</li>
 *   <li>{@link #ACCEPTED}      — actively following</li>
 * </ul>
 */
public enum FollowStatus {
    NOT_FOLLOWING,
    ACCEPTED
}
