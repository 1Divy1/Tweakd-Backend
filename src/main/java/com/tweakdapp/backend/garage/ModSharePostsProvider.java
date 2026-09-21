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
 * an already-shared one links to its post instead of silently re-posting nothing.
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
}
