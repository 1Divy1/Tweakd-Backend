package com.tweakdapp.backend.posts;

import java.util.UUID;

/**
 * Published when a post is actually shared (a new share row was inserted — a plain share or a quote
 * share; re-shares are not re-published). Consumed by the {@code notification} module to notify the
 * post's author.
 *
 * <p>The publisher already skips self-shares, so {@code recipientId != actorId} is guaranteed here.
 *
 * @param postId      the shared post
 * @param recipientId the post's author (who gets notified)
 * @param actorId     the user who shared the post
 */
public record PostSharedEvent(UUID postId, UUID recipientId, UUID actorId) {}
