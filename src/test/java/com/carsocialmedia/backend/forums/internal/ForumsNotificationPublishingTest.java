package com.carsocialmedia.backend.forums.internal;

import com.carsocialmedia.backend.forums.ForumReplyLikedEvent;
import com.carsocialmedia.backend.forums.ForumReplyRepliedEvent;
import com.carsocialmedia.backend.forums.ForumThreadLikedEvent;
import com.carsocialmedia.backend.forums.ForumThreadRepliedEvent;
import com.carsocialmedia.backend.forums.dto.request.CreateReplyRequest;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadReplyEntity;
import com.carsocialmedia.backend.forums.internal.repositories.ForumPostLikeRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumPostRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumShortcutRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadLikeRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadReadRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadSaveRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadTopicRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumTopicRepository;
import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.report.ReportService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link ForumsServiceImpl}'s reply/like writes publish the right social-notification
 * events: replies target the thread author (root) or the parent-reply author only (nested), likes
 * fire only when the {@code ON CONFLICT} insert affected a row, anonymized threads and self-actions
 * are skipped. Only the collaborators each method actually touches are stubbed.
 */
class ForumsNotificationPublishingTest {

    private static final UUID THREAD_AUTHOR = UUID.fromString("00000000-0000-0000-0000-000000000f01");
    private static final UUID PARENT_AUTHOR = UUID.fromString("00000000-0000-0000-0000-000000000f02");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000f03");
    private static final UUID THREAD = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID PARENT_REPLY = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID REPLY = UUID.fromString("00000000-0000-0000-0000-000000000103");

    private ForumThreadRepository threadRepository;
    private ForumPostRepository postRepository;
    private ForumThreadLikeRepository threadLikeRepository;
    private ForumPostLikeRepository postLikeRepository;
    private ApplicationEventPublisher eventPublisher;
    private ForumsServiceImpl service;

    private ForumThreadEntity thread(UUID authorId, boolean deleted) {
        ForumThreadEntity t = new ForumThreadEntity();
        t.setId(THREAD);
        t.setUserId(authorId);
        t.setDeleted(deleted);
        t.setLocked(false);
        return t;
    }

    private ForumThreadReplyEntity reply(UUID id, UUID authorId, UUID parentId) {
        ForumThreadReplyEntity r = new ForumThreadReplyEntity();
        r.setId(id);
        r.setThreadId(THREAD);
        r.setUserId(authorId);
        r.setParentPostId(parentId);
        r.setContent("body");
        r.setDeleted(false);
        r.setCreatedAt(Instant.now());
        return r;
    }

    @BeforeEach
    void setUp() {
        threadRepository = mock(ForumThreadRepository.class);
        postRepository = mock(ForumPostRepository.class);
        threadLikeRepository = mock(ForumThreadLikeRepository.class);
        postLikeRepository = mock(ForumPostLikeRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        service = new ForumsServiceImpl(
                mock(ForumTopicRepository.class),
                threadRepository,
                postRepository,
                mock(ForumThreadTopicRepository.class),
                threadLikeRepository,
                postLikeRepository,
                mock(ForumThreadSaveRepository.class),
                mock(ForumThreadReadRepository.class),
                mock(ForumShortcutRepository.class),
                mock(ProfileService.class),
                mock(GarageService.class),
                mock(ReportService.class),
                eventPublisher);
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));
    }

    // ---- likes: only on real insert ----------------------------------------

    @Test
    void likeThreadPublishesOnlyWhenInsertAffectedARow() {
        when(threadRepository.findById(THREAD)).thenReturn(Optional.of(thread(THREAD_AUTHOR, false)));
        when(threadLikeRepository.insertIgnoringConflict(THREAD, ACTOR)).thenReturn(1);

        service.likeThread(ACTOR.toString(), THREAD);

        ArgumentCaptor<ForumThreadLikedEvent> captor = ArgumentCaptor.forClass(ForumThreadLikedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().threadId()).isEqualTo(THREAD);
        assertThat(captor.getValue().recipientId()).isEqualTo(THREAD_AUTHOR);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR);
    }

    @Test
    void likeThreadDoesNotPublishWhenLikeAlreadyExisted() {
        when(threadRepository.findById(THREAD)).thenReturn(Optional.of(thread(THREAD_AUTHOR, false)));
        when(threadLikeRepository.insertIgnoringConflict(THREAD, ACTOR)).thenReturn(0);

        service.likeThread(ACTOR.toString(), THREAD);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void likeThreadSkipsAnonymizedThread() {
        when(threadRepository.findById(THREAD)).thenReturn(Optional.of(thread(THREAD_AUTHOR, true)));
        when(threadLikeRepository.insertIgnoringConflict(THREAD, ACTOR)).thenReturn(1);

        service.likeThread(ACTOR.toString(), THREAD);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void likeThreadDoesNotSelfNotify() {
        when(threadRepository.findById(THREAD)).thenReturn(Optional.of(thread(THREAD_AUTHOR, false)));
        when(threadLikeRepository.insertIgnoringConflict(THREAD, THREAD_AUTHOR)).thenReturn(1);

        service.likeThread(THREAD_AUTHOR.toString(), THREAD);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void likeReplyPublishesOnlyWhenInsertAffectedARow() {
        when(postRepository.findById(REPLY)).thenReturn(Optional.of(reply(REPLY, PARENT_AUTHOR, null)));
        when(postLikeRepository.insertIgnoringConflict(REPLY, ACTOR)).thenReturn(1);

        service.likePost(ACTOR.toString(), REPLY);

        ArgumentCaptor<ForumReplyLikedEvent> captor = ArgumentCaptor.forClass(ForumReplyLikedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().replyId()).isEqualTo(REPLY);
        assertThat(captor.getValue().threadId()).isEqualTo(THREAD);
        assertThat(captor.getValue().recipientId()).isEqualTo(PARENT_AUTHOR);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR);
    }

    @Test
    void likeReplyDoesNotPublishWhenLikeAlreadyExisted() {
        when(postRepository.findById(REPLY)).thenReturn(Optional.of(reply(REPLY, PARENT_AUTHOR, null)));
        when(postLikeRepository.insertIgnoringConflict(REPLY, ACTOR)).thenReturn(0);

        service.likePost(ACTOR.toString(), REPLY);

        verify(eventPublisher, never()).publishEvent(any());
    }

    // ---- replies: correct recipient by nesting ------------------------------

    @Test
    void rootReplyNotifiesThreadAuthor() {
        when(threadRepository.findById(THREAD)).thenReturn(Optional.of(thread(THREAD_AUTHOR, false)));
        when(postRepository.findById(any())).thenReturn(Optional.of(reply(REPLY, ACTOR, null)));

        service.addReply(ACTOR.toString(), THREAD, new CreateReplyRequest("great thread", null));

        ArgumentCaptor<ForumThreadRepliedEvent> captor = ArgumentCaptor.forClass(ForumThreadRepliedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().threadId()).isEqualTo(THREAD);
        assertThat(captor.getValue().recipientId()).isEqualTo(THREAD_AUTHOR);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR);
        assertThat(captor.getValue().excerpt()).isEqualTo("great thread");
    }

    @Test
    void rootReplySkippedOnAnonymizedThread() {
        when(threadRepository.findById(THREAD)).thenReturn(Optional.of(thread(THREAD_AUTHOR, true)));
        when(postRepository.findById(any())).thenReturn(Optional.of(reply(REPLY, ACTOR, null)));

        service.addReply(ACTOR.toString(), THREAD, new CreateReplyRequest("hi", null));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void nestedReplyNotifiesParentReplyAuthorOnly() {
        when(threadRepository.findById(THREAD)).thenReturn(Optional.of(thread(THREAD_AUTHOR, false)));
        // Generic stub for the newly-created reply's re-fetch; specific stub for the parent lookup.
        when(postRepository.findById(any())).thenReturn(Optional.of(reply(REPLY, ACTOR, PARENT_REPLY)));
        when(postRepository.findById(PARENT_REPLY)).thenReturn(Optional.of(reply(PARENT_REPLY, PARENT_AUTHOR, null)));

        service.addReply(ACTOR.toString(), THREAD, new CreateReplyRequest("agreed", PARENT_REPLY));

        ArgumentCaptor<ForumReplyRepliedEvent> captor = ArgumentCaptor.forClass(ForumReplyRepliedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().parentReplyId()).isEqualTo(PARENT_REPLY);
        assertThat(captor.getValue().recipientId()).isEqualTo(PARENT_AUTHOR);
        assertThat(captor.getValue().actorId()).isEqualTo(ACTOR);
        assertThat(captor.getValue().threadId()).isEqualTo(THREAD);
    }

    @Test
    void nestedReplyDoesNotSelfNotifyWhenReplyingToOwnReply() {
        when(threadRepository.findById(THREAD)).thenReturn(Optional.of(thread(THREAD_AUTHOR, false)));
        when(postRepository.findById(any())).thenReturn(Optional.of(reply(REPLY, ACTOR, PARENT_REPLY)));
        when(postRepository.findById(PARENT_REPLY)).thenReturn(Optional.of(reply(PARENT_REPLY, ACTOR, null)));

        service.addReply(ACTOR.toString(), THREAD, new CreateReplyRequest("me again", PARENT_REPLY));

        verify(eventPublisher, never()).publishEvent(any());
    }
}
