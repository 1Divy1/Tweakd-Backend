package com.carsocialmedia.backend.posts;

import java.util.UUID;

/**
 * Published when a post is actually liked (a new like row was inserted — re-likes are not
 * re-published). Consumed by the {@code notification} module to notify the post's author.
 *
 * <p>The publisher already skips self-likes, so {@code recipientId != actorId} is guaranteed here.
 *
 * @param postId      the liked post
 * @param recipientId the post's author (who gets notified)
 * @param actorId     the user who liked the post
 */
public record PostLikedEvent(UUID postId, UUID recipientId, UUID actorId) {}
