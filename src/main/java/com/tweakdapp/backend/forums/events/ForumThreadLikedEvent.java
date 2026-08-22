package com.tweakdapp.backend.forums.events;

import java.util.UUID;

/**
 * Published when a thread is actually liked (the {@code ON CONFLICT DO NOTHING} insert affected a
 * row — re-likes are not re-published). Consumed by the {@code notification} module to notify the
 * thread's author.
 *
 * <p>Not published when the thread is anonymized (soft-deleted — its author is hidden) or on a
 * self-like; the publisher does both checks, so {@code recipientId} is always a real, non-actor
 * author here.
 *
 * @param threadId    the liked thread
 * @param recipientId the thread's author (who gets notified)
 * @param actorId     the user who liked the thread
 */
public record ForumThreadLikedEvent(UUID threadId, UUID recipientId, UUID actorId) {}
