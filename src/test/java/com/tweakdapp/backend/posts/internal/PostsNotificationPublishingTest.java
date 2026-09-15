package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.mapevents.MapEventContestsService;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.posts.PostCommentedEvent;
import com.tweakdapp.backend.posts.PostLikedEvent;
import com.tweakdapp.backend.posts.PostSharedEvent;
import com.tweakdapp.backend.posts.exception.CannotRepostOwnPostException;
import com.tweakdapp.backend.posts.dto.request.CreateCommentRequest;
import com.tweakdapp.backend.posts.internal.entities.CommentEntity;
import com.tweakdapp.backend.posts.internal.entities.PostEntity;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link PostsServiceImpl}'s engagement writes publish the right social-notification
 * events at the right time: likes/shares only on a real (first) insert, comments for any nesting,
 * and never a self-notification (the actor is the post author). Only the collaborators each method
 * actually touches are stubbed.
 */
class PostsNotificationPublishingTest {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-000000000a01");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000a02");
    private static final UUID POST = UUID.fromString("00000000-0000-0000-0000-000000000b01");

    private PostRepository postRepository;
    private PostLikeRepository postLikeRepository;
    private PostShareRepository postShareRepository;
    private CommentRepository commentRepository;
    private ApplicationEventPublisher eventPublisher;
    private PostsServiceImpl service;

    private PostEntity post(UUID authorId) {
        PostEntity p = new PostEntity();
        p.setId(POST);
        p.setUserId(authorId);
        return p;
    }

    @BeforeEach
    void setUp() {
        postRepository = mock(PostRepository.class);
        postLikeRepository = mock(PostLikeRepository.class);
        postShareRepository = mock(PostShareRepository.class);
        commentRepository = mock(CommentRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new PostsServiceImpl(
                postRepository,
                mock(PostImageRepository.class),
                mock(TaggedPersonRepository.class),
                mock(TaggedCarRepository.class),
                commentRepository,
                mock(CommentLikeRepository.class),
                mock(CommentTaggedPersonRepository.class),
                mock(CommentTaggedCarRepository.class),
                postLikeRepository,
                mock(SavedPostRepository.class),
                postShareRepository,
                mock(ProfileService.class),
                mock(GarageService.class),
                mock(StorageService.class),
                mock(ReportService.class),
                eventPublisher,
                mock(MapEventContestsService.class));
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));
    }

    @Test
    void likePostPublishesPostLikedEventOnRealInsert() {
        when(postRepository.findById(POST)).thenReturn(Optional.of(post(AUTHOR)));
        when(postLikeRepository.insertIgnoringConflict(POST, ACTOR)).thenReturn(1);
        when(postLikeRepository.markLikeNotified(POST, ACTOR)).thenReturn(1);

        service.likePost(ACTOR.toString(), POST);

        ArgumentCaptor<PostLikedEvent> captor = ArgumentCaptor.forClass(PostLikedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().postId()).isEqualTo(POST);
        assertThat(captor.getValue().recipientId()).isEqualTo(AUTHOR);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR);
    }

    @Test
    void likePostDoesNotPublishForDuplicateLike() {
        when(postRepository.findById(POST)).thenReturn(Optional.of(post(AUTHOR)));
        when(postLikeRepository.insertIgnoringConflict(POST, ACTOR)).thenReturn(0);

        service.likePost(ACTOR.toString(), POST);

        verify(eventPublisher, never()).publishEvent(any());
        verify(postLikeRepository, never()).markLikeNotified(any(), any());
    }

    /** Unlike deletes the like row, so the re-like inserts again — but the author was already told. */
    @Test
    void likePostDoesNotRenotifyAfterUnlikeAndLike() {
        when(postRepository.findById(POST)).thenReturn(Optional.of(post(AUTHOR)));
        when(postLikeRepository.insertIgnoringConflict(POST, ACTOR)).thenReturn(1);
        when(postLikeRepository.markLikeNotified(POST, ACTOR)).thenReturn(0);

        service.likePost(ACTOR.toString(), POST);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void likePostDoesNotSelfNotify() {
        when(postRepository.findById(POST)).thenReturn(Optional.of(post(AUTHOR)));
        when(postLikeRepository.insertIgnoringConflict(POST, AUTHOR)).thenReturn(1);
        when(postLikeRepository.markLikeNotified(POST, AUTHOR)).thenReturn(1);

        service.likePost(AUTHOR.toString(), POST);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void sharePostPublishesPostSharedEventOnRealInsert() {
        when(postRepository.findById(POST)).thenReturn(Optional.of(post(AUTHOR)));
        when(postShareRepository.insertIgnoringConflict(POST, ACTOR)).thenReturn(1);
        when(postShareRepository.markRepostNotified(POST, ACTOR)).thenReturn(1);

        service.sharePost(ACTOR.toString(), POST);

        ArgumentCaptor<PostSharedEvent> captor = ArgumentCaptor.forClass(PostSharedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().recipientId()).isEqualTo(AUTHOR);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR);
    }

    @Test
    void sharePostDoesNotPublishForDuplicateShare() {
        when(postRepository.findById(POST)).thenReturn(Optional.of(post(AUTHOR)));
        when(postShareRepository.insertIgnoringConflict(POST, ACTOR)).thenReturn(0);

        service.sharePost(ACTOR.toString(), POST);

        verify(eventPublisher, never()).publishEvent(any());
        verify(postShareRepository, never()).markRepostNotified(any(), any());
    }

    /** Undo deletes the repost row, so the re-repost inserts again — but the author was already told. */
    @Test
    void sharePostDoesNotRenotifyAfterUndoAndRepost() {
        when(postRepository.findById(POST)).thenReturn(Optional.of(post(AUTHOR)));
        when(postShareRepository.insertIgnoringConflict(POST, ACTOR)).thenReturn(1);
        when(postShareRepository.markRepostNotified(POST, ACTOR)).thenReturn(0);

        service.sharePost(ACTOR.toString(), POST);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void repostingYourOwnPostIsRejectedAndWritesNothing() {
        when(postRepository.findById(POST)).thenReturn(Optional.of(post(AUTHOR)));

        assertThatThrownBy(() -> service.sharePost(AUTHOR.toString(), POST))
                .isInstanceOf(CannotRepostOwnPostException.class);

        verify(postShareRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void addCommentNotifiesPostAuthorForAnyNesting() {
        when(postRepository.findById(POST)).thenReturn(Optional.of(post(AUTHOR)));
        CommentEntity hydrated = new CommentEntity();
        hydrated.setPostId(POST);
        hydrated.setUserId(ACTOR);
        hydrated.setContent("first!");
        hydrated.setCreatedAt(Instant.now());
        when(commentRepository.findById(any())).thenReturn(Optional.of(hydrated));

        service.addComment(ACTOR.toString(), POST, new CreateCommentRequest("first!", null, null, null));

        ArgumentCaptor<PostCommentedEvent> captor = ArgumentCaptor.forClass(PostCommentedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().postId()).isEqualTo(POST);
        assertThat(captor.getValue().recipientId()).isEqualTo(AUTHOR);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR);
        assertThat(captor.getValue().excerpt()).isEqualTo("first!");
        assertThat(captor.getValue().commentId()).isNotNull();
    }

    @Test
    void addCommentDoesNotSelfNotifyWhenAuthorCommentsOnOwnPost() {
        when(postRepository.findById(POST)).thenReturn(Optional.of(post(AUTHOR)));
        CommentEntity hydrated = new CommentEntity();
        hydrated.setPostId(POST);
        hydrated.setUserId(AUTHOR);
        hydrated.setContent("mine");
        hydrated.setCreatedAt(Instant.now());
        when(commentRepository.findById(any())).thenReturn(Optional.of(hydrated));

        service.addComment(AUTHOR.toString(), POST, new CreateCommentRequest("mine", null, null, null));

        verify(eventPublisher, never()).publishEvent(any());
    }
}
