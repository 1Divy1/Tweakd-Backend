package com.carsocialmedia.backend.forums.events;

import java.util.UUID;

/**
 * Published when someone replies to an existing reply (a nested reply). Consumed by the
 * {@code notification} module to notify the PARENT REPLY's author only — the thread author is
 * deliberately NOT notified for nested replies.
 *
 * <p>The parent reply is guaranteed live (replying under a deleted reply is rejected) and the
 * publisher skips self-replies, so {@code recipientId} is always a real, non-actor author here.
 *
 * @param threadId      the enclosing thread
 * @param parentReplyId the reply being replied to
 * @param replyId       the new reply
 * @param recipientId   the parent reply's author (who gets notified)
 * @param actorId       the replying user
 * @param excerpt       the (possibly truncated) reply text for the notification body, or {@code null}
 */
public record ForumReplyRepliedEvent(UUID threadId, UUID parentReplyId, UUID replyId, UUID recipientId, UUID actorId,
                                     String excerpt) {}
