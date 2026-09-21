package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.garage.ModSharePostsProvider;
import com.tweakdapp.backend.posts.internal.repositories.PostRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tells the garage module which of a car's modifications have already been shared to the feed.
 *
 * <p>The inverse of the share itself: posts reads the build log through {@code GarageService}, and
 * garage reads this one fact back through {@link ModSharePostsProvider} rather than touching the
 * posts tables.
 */
@Component
class ModSharePostsAdapter implements ModSharePostsProvider {

    private final PostRepository postRepository;

    ModSharePostsAdapter(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UUID> findPostIdsByModificationIds(Collection<UUID> modificationIds) {
        if (modificationIds == null || modificationIds.isEmpty()) {
            return Map.of();
        }
        return postRepository.findModSharePostRefs(modificationIds).stream()
                .collect(Collectors.toMap(
                        PostRepository.ModSharePostRef::getModificationId,
                        PostRepository.ModSharePostRef::getPostId,
                        // A partial unique index makes a duplicate impossible; keep the first if the
                        // index is ever dropped rather than throwing on a read path.
                        (first, second) -> first));
    }
}
