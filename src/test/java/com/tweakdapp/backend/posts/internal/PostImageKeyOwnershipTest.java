package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.mapevents.MapEventContestsService;
import com.tweakdapp.backend.posts.exception.InvalidReferenceException;
import com.tweakdapp.backend.posts.exception.NotPostOwnerException;
import com.tweakdapp.backend.posts.exception.PostNotFoundException;
import com.tweakdapp.backend.posts.internal.entities.PostEntity;
import com.tweakdapp.backend.posts.internal.entities.PostImageEntity;
import com.tweakdapp.backend.posts.internal.repositories.CommentLikeRepository;
import com.tweakdapp.backend.posts.internal.repositories.CommentRepository;
import com.tweakdapp.backend.posts.internal.repositories.CommentTaggedCarRepository;
import com.tweakdapp.backend.posts.internal.repositories.CommentTaggedPersonRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostImageRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostLikeRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostShareRepository;
import com.tweakdapp.backend.posts.internal.repositories.SavedPostRepository;
import com.tweakdapp.backend.posts.internal.repositories.TaggedCarRepository;
import com.tweakdapp.backend.posts.internal.repositories.TaggedPersonRepository;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.report.ReportService;
import com.tweakdapp.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Post image keys come back from the client, and an edit deletes from R2 every stored key the new
 * list drops. Accepting a key minted for another post would let an author attach that image and then
 * delete it, so only keys under {@code posts/{postId}/} (or already on the post) are accepted.
 */
class PostImageKeyOwnershipTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-0000-0000-000000000b01");
    private static final UUID OTHER_POST = UUID.fromString("00000000-0000-0000-0000-000000000b02");

    private PostRepository postRepository;
    private PostImageRepository postImageRepository;
    private StorageService storageService;
    private PostsServiceImpl service;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        postImageRepository = mock(PostImageRepository.class);
        storageService = mock(StorageService.class);

        service = new PostsServiceImpl(
                postRepository,
                postImageRepository,
                mock(TaggedPersonRepository.class),
                mock(TaggedCarRepository.class),
                mock(CommentRepository.class),
                mock(CommentLikeRepository.class),
                mock(CommentTaggedPersonRepository.class),
                mock(CommentTaggedCarRepository.class),
                mock(PostLikeRepository.class),
                mock(SavedPostRepository.class),
                mock(PostShareRepository.class),
                mock(ProfileService.class),
                mock(GarageService.class),
                storageService,
                mock(ReportService.class),
                mock(ApplicationEventPublisher.class),
                mock(MapEventContestsService.class));
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        PostEntity post = new PostEntity();
        post.setId(POST_ID);
        post.setUserId(AUTHOR);
        post.setDescription("");
        post.setLikesCount(0L);
        post.setCommentsCount(0L);
        post.setSharesCount(0L);
        post.setSavedCount(0L);
        post.setLikesCountEnabled(true);
        post.setCommentsCountEnabled(true);
        post.setSharesCountEnabled(true);
        post.setSavedCountEnabled(true);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void anImageKeyMintedForAnotherPostIsRejectedBeforeAnythingIsRemoved() {
        String own = "posts/" + POST_ID + "/a.webp";
        when(postImageRepository.findAllByPostIdOrderByDisplayOrderAsc(POST_ID)).thenReturn(List.of(image(own)));

        assertThatThrownBy(() -> service.savePostImageKeys(AUTHOR.toString(), POST_ID,
                List.of("posts/" + OTHER_POST + "/b.webp")))
                .isInstanceOf(InvalidReferenceException.class);
        commit();

        verify(postImageRepository, never()).deleteAllByPostId(any());
        verify(storageService, never()).deleteByKeys(any(), any());
    }

    @Test
    void keysAlreadyOnThePostOrUnderItsPrefixAreSaved() {
        String stored = "legacy/" + POST_ID + "/a.webp";
        when(postImageRepository.findAllByPostIdOrderByDisplayOrderAsc(POST_ID)).thenReturn(List.of(image(stored)));

        assertThatCode(() -> service.savePostImageKeys(AUTHOR.toString(), POST_ID,
                List.of(stored, "posts/" + POST_ID + "/b.webp")))
                .doesNotThrowAnyException();

        verify(postImageRepository).saveAll(any());
    }

    @Test
    void onlyTheAuthorIsHandedPostUploadUrls() {
        PostUploadAccessPolicy policy = new PostUploadAccessPolicy(postRepository);
        UUID missing = UUID.randomUUID();
        when(postRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatCode(() -> policy.requireUploadAccess(AUTHOR, POST_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireUploadAccess(STRANGER, POST_ID))
                .isInstanceOf(NotPostOwnerException.class);
        assertThatThrownBy(() -> policy.requireUploadAccess(AUTHOR, missing))
                .isInstanceOf(PostNotFoundException.class);
    }

    private static PostImageEntity image(String key) {
        PostImageEntity image = new PostImageEntity();
        image.setId(UUID.randomUUID());
        image.setPostId(POST_ID);
        image.setImageKey(key);
        return image;
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }
}
