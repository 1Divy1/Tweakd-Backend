package com.carsocialmedia.backend.posts;

import java.util.UUID;

/**
 * Published when a user is tagged in a post comment — either as a person, or by having one of their
 * cars tagged (a car's owner is always tagged as a person too, unless the car is the commenter's
 * own, so one event per recipient covers both). Consumed by the {@code notification} module.
 *
 * <p>Comments cannot be edited, so every tag is new at publish time. The publisher skips self-tags,
 * so {@code recipientId} is always a real, non-actor user here.
 *
 * @param postId      the post the comment belongs to (for deep-linking)
 * @param commentId   the comment the recipient was tagged in
 * @param recipientId the tagged user (who gets notified)
 * @param actorId     the comment's author, who did the tagging
 * @param carTagged   whether at least one of the recipient's cars is among the tags (drives the
 *                    "tagged your car" vs "tagged you" wording)
 */
public record PostCommentTaggedEvent(UUID postId, UUID commentId, UUID recipientId, UUID actorId, boolean carTagged) {}
