package com.carsocialmedia.backend.forums;

import java.util.UUID;

/**
 * Published when a reply is actually liked (the {@code ON CONFLICT DO NOTHING} insert affected a
 * row — re-likes are not re-published). Consumed by the {@code notification} module to notify the
 * reply's author.
 *
 * <p>A likeable reply is guaranteed live (liking a deleted reply is rejected) and the publisher
 * skips self-likes, so {@code recipientId} is always a real, non-actor author here.
 *
 * @param replyId     the liked reply
 * @param threadId    the enclosing thread (for client deep-linking)
 * @param recipientId the reply's author (who gets notified)
 * @param actorId     the user who liked the reply
 */
public record ForumReplyLikedEvent(UUID replyId, UUID threadId, UUID recipientId, UUID actorId) {}
