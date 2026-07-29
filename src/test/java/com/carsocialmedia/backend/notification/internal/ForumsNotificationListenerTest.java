package com.carsocialmedia.backend.notification.internal;

import com.carsocialmedia.backend.forums.events.ForumReplyLikedEvent;
import com.carsocialmedia.backend.forums.events.ForumReplyRepliedEvent;
import com.carsocialmedia.backend.forums.events.ForumThreadLikedEvent;
import com.carsocialmedia.backend.forums.events.ForumThreadRepliedEvent;
import com.carsocialmedia.backend.notification.NotificationService;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.NotificationPreferencesDto;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ForumsNotificationListener}: preference gating (replies gate on comments,
 * likes on likes; enabled / disabled / missing-row-as-enabled), payload contents and titles/bodies.
 */
class ForumsNotificationListenerTest {

    private static final UUID RECIPIENT = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID THREAD = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID REPLY = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
    private static final UUID PARENT_REPLY = UUID.fromString("00000000-0000-0000-0000-0000000000e3");

    private ProfileService profileService;
    private NotificationService notificationService;
    private ForumsNotificationListener listener;

    private static NotificationPreferencesDto allEnabled() {
        return new NotificationPreferencesDto(true, true, true, true, true, true, true, true);
    }

    private static NotificationPreferencesDto with(boolean likes, boolean comments) {
        return new NotificationPreferencesDto(likes, comments, true, true, true, true, true, true);
    }

    @BeforeEach
    void setUp() {
        profileService = mock(ProfileService.class);
        notificationService = mock(NotificationService.class);
        listener = new ForumsNotificationListener(profileService, notificationService);
        when(profileService.findByIds(anyCollection()))
                .thenReturn(List.of(new ProfileSearchResultDto(ACTOR, "marius_dev", null)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void threadReplyGatesOnCommentsAndCarriesThreadAndReplyIds() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(allEnabled());

        listener.on(new ForumThreadRepliedEvent(THREAD, REPLY, RECIPIENT, ACTOR, "good point"));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).push(eq(RECIPIENT), eq("forum_thread_reply"),
                eq("marius_dev replied to your thread"), eq("good point"), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("actor_id", ACTOR.toString())
                .containsEntry("actor_username", "marius_dev")
                .containsEntry("thread_id", THREAD.toString())
                .containsEntry("reply_id", REPLY.toString());
    }

    @Test
    void threadReplySkippedWhenCommentsDisabled() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(with(true, false));

        listener.on(new ForumThreadRepliedEvent(THREAD, REPLY, RECIPIENT, ACTOR, "x"));

        verifyNoInteractions(notificationService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replyReplyGatesOnCommentsAndCarriesParentReplyId() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(allEnabled());

        listener.on(new ForumReplyRepliedEvent(THREAD, PARENT_REPLY, REPLY, RECIPIENT, ACTOR, "agreed"));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).push(eq(RECIPIENT), eq("forum_reply_reply"),
                eq("marius_dev replied to your reply"), eq("agreed"), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("thread_id", THREAD.toString())
                .containsEntry("parent_reply_id", PARENT_REPLY.toString())
                .containsEntry("reply_id", REPLY.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void threadLikeGatesOnLikesAndCarriesThreadId() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(allEnabled());

        listener.on(new ForumThreadLikedEvent(THREAD, RECIPIENT, ACTOR));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).push(eq(RECIPIENT), eq("forum_thread_like"),
                eq("marius_dev liked your thread"), isNull(), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("thread_id", THREAD.toString())
                .doesNotContainKey("reply_id");
    }

    @Test
    void threadLikeSkippedWhenLikesDisabled() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(with(false, true));

        listener.on(new ForumThreadLikedEvent(THREAD, RECIPIENT, ACTOR));

        verifyNoInteractions(notificationService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void replyLikeGatesOnLikesAndCarriesThreadAndReplyIds() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(allEnabled());

        listener.on(new ForumReplyLikedEvent(REPLY, THREAD, RECIPIENT, ACTOR));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).push(eq(RECIPIENT), eq("forum_reply_like"),
                eq("marius_dev liked your reply"), isNull(), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("thread_id", THREAD.toString())
                .containsEntry("reply_id", REPLY.toString());
    }

    @Test
    void replyLikeSkippedWhenLikesDisabled() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(with(false, true));

        listener.on(new ForumReplyLikedEvent(REPLY, THREAD, RECIPIENT, ACTOR));

        verifyNoInteractions(notificationService);
    }

    @Test
    void missingPreferencesRowReadsAsEnabledSoNotificationStillFires() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(allEnabled());

        listener.on(new ForumThreadLikedEvent(THREAD, RECIPIENT, ACTOR));

        verify(notificationService).push(eq(RECIPIENT), eq("forum_thread_like"), any(), isNull(), any());
    }
}
