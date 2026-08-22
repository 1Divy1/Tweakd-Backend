package com.tweakdapp.backend.forums.events;

import java.util.UUID;

/**
 * Published when a root-level reply is added to a thread. Consumed by the {@code notification}
 * module to notify the THREAD's author.
 *
 * <p>Not published when the thread is anonymized (soft-deleted — its author is hidden) or when the
 * author replies to their own thread; the publisher does both checks, so {@code recipientId} is
 * always a real, non-actor author here.
 *
 * @param threadId    the thread being replied to
 * @param replyId     the new reply
 * @param recipientId the thread's author (who gets notified)
 * @param actorId     the replying user
 * @param excerpt     the (possibly truncated) reply text for the notification body, or {@code null}
 */
public record ForumThreadRepliedEvent(UUID threadId, UUID replyId, UUID recipientId, UUID actorId, String excerpt) {}
