package com.tweakdapp.backend.garage;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Which build-log modifications have already been shared to the feed, and as which post.
 *
 * <p>Garage owns the build log but not posts, and it cannot depend on the posts module: that module
 * already depends on this one. So garage declares what it needs and posts implements it. It's the
 * same arrangement as {@link PublicCarEventsProvider} and {@code storage.UploadAccessPolicy}.
 *
 * <p>The app needs this to draw a mod's share affordance honestly: an unshared mod offers to share,
 * an already-shared one links to its post instead of silently re-posting nothing. Deleting a car
 * uses it too, to take the feed posts sharing its mods down with it.
 */
public interface ModSharePostsProvider {

    /**
     * Post id by modification id, for those that have been shared. Modifications with no post are
     * absent from the result rather than mapped to null.
     *
     * @param modificationIds the modifications to look up
     * @return post id by modification id (may be empty)
     */
    Map<UUID, UUID> findPostIdsByModificationIds(Collection<UUID> modificationIds);

    /**
     * Deletes the feed posts that share any of these modifications, with their likes, comments and
     * images. Runs in the caller's transaction, so the posts go only if the caller commits.
     *
     * <p>Used when a whole car is deleted: a mod-share post is a card drawn from the build log, and
     * with the car gone it would be left as an empty shell. Deleting a single mod does not call
     * this — that post falls back to a plain post via {@code ON DELETE SET NULL}.
     *
     * @param modificationIds the modifications whose share posts should go
     */
    void deleteSharePosts(Collection<UUID> modificationIds);
}
