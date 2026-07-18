package com.carsocialmedia.backend.notification.internal;

import com.carsocialmedia.backend.notification.NotificationService;
import com.carsocialmedia.backend.posts.PostCommentedEvent;
import com.carsocialmedia.backend.posts.PostLikedEvent;
import com.carsocialmedia.backend.posts.PostSharedEvent;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PostsNotificationListener}: preference gating (enabled / disabled / a
 * missing row read as all-enabled), payload contents, body handling and the actor-username resolve.
 * The handlers are invoked directly (no async / transaction), which is what the {@code @Async
 * @TransactionalEventListener} wiring dispatches to.
 */
class PostsNotificationListenerTest {

    private static final UUID RECIPIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID POST = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID COMMENT = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    private ProfileService profileService;
    private NotificationService notificationService;
    private PostsNotificationListener listener;

    private static NotificationPreferencesDto allEnabled() {
        return new NotificationPreferencesDto(true, true, true, true, true, true, true);
    }

    private static NotificationPreferencesDto with(boolean likes, boolean comments, boolean shares) {
        return new NotificationPreferencesDto(likes, comments, shares, true, true, true, true);
    }

    @BeforeEach
    void setUp() {
        profileService = mock(ProfileService.class);
        notificationService = mock(NotificationService.class);
        listener = new PostsNotificationListener(profileService, notificationService);
        when(profileService.findByIds(anyCollection()))
                .thenReturn(List.of(new ProfileSearchResultDto(ACTOR, "marius_dev", null)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void postLikePushesWithActorTitleAndPayloadWhenEnabled() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(allEnabled());

        listener.on(new PostLikedEvent(POST, RECIPIENT, ACTOR));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).push(eq(RECIPIENT), eq("post_like"),
                eq("marius_dev liked your post"), isNull(), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("actor_id", ACTOR.toString())
                .containsEntry("actor_username", "marius_dev")
                .containsEntry("post_id", POST.toString())
                .doesNotContainKey("comment_id");
    }

    @Test
    void postLikeSkippedWhenLikesDisabled() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(with(false, true, true));

        listener.on(new PostLikedEvent(POST, RECIPIENT, ACTOR));

        verifyNoInteractions(notificationService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void postCommentCarriesExcerptBodyAndCommentIdWhenEnabled() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(allEnabled());

        listener.on(new PostCommentedEvent(POST, COMMENT, RECIPIENT, ACTOR, "nice ride!"));

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).push(eq(RECIPIENT), eq("post_comment"),
                eq("marius_dev commented on your post"), eq("nice ride!"), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("post_id", POST.toString())
                .containsEntry("comment_id", COMMENT.toString())
                .containsEntry("actor_id", ACTOR.toString());
    }

    @Test
    void postCommentSkippedWhenCommentsDisabled() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(with(true, false, true));

        listener.on(new PostCommentedEvent(POST, COMMENT, RECIPIENT, ACTOR, "hey"));

        verifyNoInteractions(notificationService);
    }

    @Test
    void postSharePushesWhenEnabledWithNullBody() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(allEnabled());

        listener.on(new PostSharedEvent(POST, RECIPIENT, ACTOR));

        verify(notificationService).push(eq(RECIPIENT), eq("post_share"),
                eq("marius_dev shared your post"), isNull(), any());
    }

    @Test
    void postShareSkippedWhenSharesDisabled() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(with(true, true, false));

        listener.on(new PostSharedEvent(POST, RECIPIENT, ACTOR));

        verifyNoInteractions(notificationService);
    }

    @Test
    void missingPreferencesRowReadsAsEnabledSoNotificationStillFires() {
        // getNotificationPreferencesOrDefault maps a missing row to all-enabled; the listener must
        // then push (the default treatment).
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(allEnabled());

        listener.on(new PostLikedEvent(POST, RECIPIENT, ACTOR));

        verify(notificationService).push(eq(RECIPIENT), eq("post_like"), any(), isNull(), any());
    }

    @Test
    void unresolvableActorFallsBackToGenericDisplayName() {
        when(profileService.getNotificationPreferencesOrDefault(RECIPIENT)).thenReturn(allEnabled());
        when(profileService.findByIds(anyCollection())).thenReturn(List.of());

        listener.on(new PostLikedEvent(POST, RECIPIENT, ACTOR));

        verify(notificationService).push(eq(RECIPIENT), eq("post_like"),
                eq("Someone liked your post"), isNull(), any());
    }
}
