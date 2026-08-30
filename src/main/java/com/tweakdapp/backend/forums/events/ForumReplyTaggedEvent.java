package com.tweakdapp.backend.forums.events;

import java.util.UUID;

/**
 * Published when a user is newly tagged in a thread reply — either as a person, or by having one of
 * their cars tagged (a car's owner is always tagged as a person too, so one event per recipient
 * covers both). Consumed by the {@code notification} module.
 *
 * <p>Published on create and on edit, but only for tags that are <em>new</em>: re-saving a reply
 * with an unchanged tag set notifies nobody. The publisher skips self-tags, so {@code recipientId}
 * is always a real, non-actor user here.
 *
 * @param threadId    the thread the reply belongs to (for deep-linking)
 * @param replyId     the reply the recipient was tagged in
 * @param recipientId the tagged user (who gets notified)
 * @param actorId     the reply's author, who did the tagging
 * @param carTagged   whether at least one of the recipient's cars is among the new tags (drives the
 *                    "tagged your car" vs "tagged you" wording)
 */
public record ForumReplyTaggedEvent(UUID threadId, UUID replyId, UUID recipientId, UUID actorId, boolean carTagged) {}
