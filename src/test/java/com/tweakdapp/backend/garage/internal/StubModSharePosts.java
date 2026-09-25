package com.tweakdapp.backend.garage.internal;

import com.tweakdapp.backend.garage.ModSharePostsProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Stands in for the posts module behind {@link ModSharePostsProvider}: answers lookups from a map
 * read at call time, and records which modifications a car delete asked to take down.
 */
final class StubModSharePosts implements ModSharePostsProvider {

    private final Supplier<Map<UUID, UUID>> sharedPosts;
    final List<Collection<UUID>> deleteCalls = new ArrayList<>();

    StubModSharePosts(Supplier<Map<UUID, UUID>> sharedPosts) {
        this.sharedPosts = sharedPosts;
    }

    /** Nothing shared. */
    static StubModSharePosts none() {
        return new StubModSharePosts(Map::of);
    }

    @Override
    public Map<UUID, UUID> findPostIdsByModificationIds(Collection<UUID> modificationIds) {
        return sharedPosts.get();
    }

    @Override
    public void deleteSharePosts(Collection<UUID> modificationIds) {
        deleteCalls.add(List.copyOf(modificationIds));
    }
}
