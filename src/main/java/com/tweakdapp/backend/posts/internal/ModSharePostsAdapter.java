package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.garage.ModSharePostsProvider;
import com.tweakdapp.backend.posts.internal.entities.PostImageEntity;
import com.tweakdapp.backend.posts.internal.repositories.PostImageRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostRepository;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tells the garage module which of a car's modifications have already been shared to the feed.
 *
 * <p>The inverse of the share itself: posts reads the build log through {@code GarageService}, and
 * garage reads this one fact back through {@link ModSharePostsProvider} rather than touching the
 * posts tables. The same bridge lets a car delete take its mod-share posts with it.
 *
 * <p>It works on the repositories directly rather than through {@code PostsService}: that service
 * depends on {@code GarageService}, which depends on this, so going through it would be a cycle.
 */
@Component
class ModSharePostsAdapter implements ModSharePostsProvider {

    private static final Logger log = LoggerFactory.getLogger(ModSharePostsAdapter.class);

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final StorageService storageService;

    ModSharePostsAdapter(PostRepository postRepository,
                         PostImageRepository postImageRepository,
                         StorageService storageService) {
        this.postRepository = postRepository;
        this.postImageRepository = postImageRepository;
        this.storageService = storageService;
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

    @Override
    @Transactional
    public void deleteSharePosts(Collection<UUID> modificationIds) {
        if (modificationIds == null || modificationIds.isEmpty()) {
            return;
        }
        List<UUID> postIds = postRepository.findModSharePostRefs(modificationIds).stream()
                .map(PostRepository.ModSharePostRef::getPostId)
                .toList();
        if (postIds.isEmpty()) {
            return;
        }

        // Same order as PostsService.deletePost: collect the R2 keys before the rows go. Likes,
        // comments, saves, shares and reports are removed by ON DELETE CASCADE; R2 is not.
        List<String> imageKeys = postImageRepository.findAllByPostIdInOrderByPostIdAscDisplayOrderAsc(postIds)
                .stream()
                .map(PostImageEntity::getImageKey)
                .toList();

        postRepository.deleteAllByIdInBatch(postIds);

        if (imageKeys.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.deleteByKeys(StorageBucket.POSTS, imageKeys);
                } catch (Exception e) {
                    log.warn("DB committed but failed to delete {} orphaned R2 objects for {} mod-share posts",
                            imageKeys.size(), postIds.size(), e);
                }
            }
        });
    }
}
