package com.tweakdapp.backend.relationships;

import com.tweakdapp.backend.relationships.dto.FollowProfileSearchResult;
import com.tweakdapp.backend.relationships.dto.FollowStatusDto;

import java.util.List;
import java.util.UUID;

public interface RelationshipService {

    /**
     * Follows the user identified by {@code targetUsername} on behalf of the current user. All
     * accounts are public, so a follow takes effect immediately (status {@code accepted}); there
     * are no follow requests to approve. Idempotent — re-following returns the existing status.
     */
    FollowStatusDto follow(String currentUserId, String targetUsername);

    /**
     * Removes the follow relationship from the current user toward {@code targetUsername}.
     * Idempotent — a no-op if no row exists.
     */
    void unfollow(String currentUserId, String targetUsername);

    /** Current relationship status from {@code currentUserId} toward {@code targetUsername}. */
    FollowStatusDto getFollowStatus(String currentUserId, String targetUsername);

    /** Followers of {@code targetUsername}. All accounts are public, so the list is always visible. */
    List<FollowProfileSearchResult> getFollowers(String currentUserId, String targetUsername);

    /** Users that {@code targetUsername} follows. All accounts are public, so always visible. */
    List<FollowProfileSearchResult> getFollowing(String currentUserId, String targetUsername);

    /**
     * Ids of the accounts {@code userId} follows, most recent follow first. An id-only read for
     * modules that shape content around who someone follows (the feed's reposts), so they never
     * touch the {@code follows} table themselves.
     */
    List<UUID> findFollowingIds(UUID userId);

    /**
     * Removes a follower from the current user's list of followers.
     * @param currentUserId the ID of the current user
     * @param followerUsernameToRemove the username of the follower to remove
     */
    void removeFollower(String currentUserId, String followerUsernameToRemove);
}
