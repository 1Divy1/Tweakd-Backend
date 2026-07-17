package com.carsocialmedia.backend.posts;

import java.util.UUID;

/**
 * Published when a comment (or a threaded reply — any nesting) is added to a post. Consumed by the
 * {@code notification} module to notify the POST's author only, regardless of comment nesting.
 *
 * <p>The publisher already skips the author commenting on their own post, so
 * {@code recipientId != actorId} is guaranteed here.
 *
 * @param postId      the commented-on post
 * @param commentId   the new comment
 * @param recipientId the post's author (who gets notified)
 * @param actorId     the commenting user
 * @param excerpt     the (possibly truncated) comment text for the notification body, or {@code null}
 */
public record PostCommentedEvent(UUID postId, UUID commentId, UUID recipientId, UUID actorId, String excerpt) {}
