package com.tweakdapp.backend.relationships;

import com.tweakdapp.backend.relationships.dto.BlockedAccountDto;

import java.util.List;

/**
 * Blocking accounts. A block is two-way in effect: neither account sees the other's profile,
 * content, or search result, and they cannot message each other. The blocked account is never
 * notified. Enforcement on reads lives with each content module, through
 * {@link com.tweakdapp.backend.shared.blocking.BlockDirectory}.
 */
public interface BlockService {

    /**
     * Blocks {@code targetUsername} on behalf of the current user and removes any follow between the
     * two, in both directions (unblocking does not restore it). Idempotent — blocking an account
     * already blocked is a no-op.
     *
     * @throws com.tweakdapp.backend.relationships.exception.CannotBlockSelfException for your own account
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if the username does not resolve
     */
    void block(String currentUserId, String targetUsername);

    /** Lifts the current user's block on {@code targetUsername}. Idempotent. */
    void unblock(String currentUserId, String targetUsername);

    /** The accounts the current user has blocked, most recent first. */
    List<BlockedAccountDto> listBlocked(String currentUserId);
}
