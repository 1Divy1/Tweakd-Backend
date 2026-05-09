package com.carsocialmedia.backend.follow;

import com.carsocialmedia.backend.profile.ProfileSearchResultDto;

import java.util.List;

public interface FollowService {

    /**
     * Sends a follow request from the current user to the user identified by {@code targetUsername}.
     * <p>
     * The Supabase {@code set_follow_initial_status} trigger sets {@code status} to
     * {@code accepted} for public targets and {@code pending} for private ones, so the
     * resulting status is read back from the row.
     */
    FollowStatusDto follow(String currentUserId, String targetUsername);

    /**
     * Removes any follow relationship (accepted or pending) from the current user toward
     * {@code targetUsername}. Idempotent — a no-op if no row exists.
     */
    void unfollow(String currentUserId, String targetUsername);

    /** Current relationship status from {@code currentUserId} toward {@code targetUsername}. */
    FollowStatusDto getFollowStatus(String currentUserId, String targetUsername);

    /** Pending follow requests received by the current user. */
    List<FollowRequestDto> getPendingRequests(String currentUserId);

    /** Accepts a pending request from {@code requesterUsername}. */
    void acceptRequest(String currentUserId, String requesterUsername);

    /**
     * Rejects (removes) a pending request from {@code requesterUsername}. Same effect
     * as the requester unfollowing — counters are not decremented because the request
     * was never accepted.
     */
    void rejectRequest(String currentUserId, String requesterUsername);

    /**
     * Followers of {@code targetUsername}. If the target account is private, the list
     * is only visible to the owner or to users who are already accepted followers.
     */
    List<ProfileSearchResultDto> getFollowers(String currentUserId, String targetUsername);

    /**
     * Users that {@code targetUsername} follows. Same privacy rules as
     * {@link #getFollowers(String, String)}.
     */
    List<ProfileSearchResultDto> getFollowing(String currentUserId, String targetUsername);
}
