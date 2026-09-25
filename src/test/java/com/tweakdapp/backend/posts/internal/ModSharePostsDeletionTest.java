package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.posts.internal.entities.PostImageEntity;
import com.tweakdapp.backend.posts.internal.repositories.PostImageRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostRepository;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * When a car is deleted, the posts sharing its mods go with it: the rows in the car's transaction,
 * their images from R2 only once that transaction commits.
 */
class ModSharePostsDeletionTest {

    private static final UUID MOD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final String IMAGE_KEY = "posts/" + POST_ID + "/1.webp";

    private PostRepository postRepository;
    private PostImageRepository postImageRepository;
    private StorageService storageService;
    private ModSharePostsAdapter adapter;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        postImageRepository = mock(PostImageRepository.class);
        storageService = mock(StorageService.class);
        adapter = new ModSharePostsAdapter(postRepository, postImageRepository, storageService);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void deletesTheSharePostsAndTheirImagesAfterCommit() {
        PostRepository.ModSharePostRef ref = mock(PostRepository.ModSharePostRef.class);
        when(ref.getModificationId()).thenReturn(MOD_ID);
        when(ref.getPostId()).thenReturn(POST_ID);
        when(postRepository.findModSharePostRefs(List.of(MOD_ID))).thenReturn(List.of(ref));
        PostImageEntity image = mock(PostImageEntity.class);
        when(image.getImageKey()).thenReturn(IMAGE_KEY);
        when(postImageRepository.findAllByPostIdInOrderByPostIdAscDisplayOrderAsc(List.of(POST_ID)))
                .thenReturn(List.of(image));

        adapter.deleteSharePosts(List.of(MOD_ID));

        verify(postRepository).deleteAllByIdInBatch(List.of(POST_ID));
        // R2 is untouched until the transaction commits.
        verifyNoInteractions(storageService);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storageService).deleteByKeys(StorageBucket.POSTS, List.of(IMAGE_KEY));
    }

    @Test
    void doesNothingWhenNoModWasShared() {
        when(postRepository.findModSharePostRefs(List.of(MOD_ID))).thenReturn(List.of());

        adapter.deleteSharePosts(List.of(MOD_ID));

        verify(postRepository, never()).deleteAllByIdInBatch(any());
        verifyNoInteractions(storageService);
    }

    @Test
    void aCarWithNoModsSkipsTheLookup() {
        adapter.deleteSharePosts(List.of());

        verify(postRepository, never()).findModSharePostRefs(anyCollection());
    }
}
