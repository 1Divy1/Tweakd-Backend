package com.carsocialmedia.backend.follow.internal;

import com.carsocialmedia.backend.profile.event.ProfileBecamePublicEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to profile-side domain events that affect the follow graph.
 */
@Component
class FollowEventListener {

    private final FollowServiceImpl followService;

    FollowEventListener(FollowServiceImpl followService) {
        this.followService = followService;
    }

    /**
     * When a profile transitions from private to public, all pending requests
     * targeted at it should be auto-accepted. The trigger
     * {@code handle_follow_change} on UPDATE pending → accepted increments
     * the counters per row, so we don't touch them from Java.
     */
    @EventListener
    void onProfileBecamePublic(ProfileBecamePublicEvent event) {
        followService.acceptAllPendingFor(event.userId());
    }
}
