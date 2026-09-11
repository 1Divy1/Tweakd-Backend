package com.tweakdapp.backend.notification.internal;

import com.tweakdapp.backend.notification.dto.NotificationDto;
import com.tweakdapp.backend.notification.internal.entities.NotificationEntity;
import com.tweakdapp.backend.notification.internal.repositories.NotificationRepository;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The list endpoint resolves each actor's <em>current</em> avatar at read time rather than trusting
 * a snapshot in the stored payload — avatar objects are deleted when replaced, so a stored URL would
 * go dead the moment its owner changed their photo.
 */
class NotificationListActorAvatarTest {

    private static final UUID RECIPIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID DAVID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID NO_PHOTO = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private NotificationRepository notificationRepository;
    private ProfileService profileService;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        profileService = mock(ProfileService.class);
        service = new NotificationServiceImpl(notificationRepository, mock(ApplicationEventPublisher.class),
                profileService);
    }

    private static NotificationEntity row(String type, Map<String, Object> payload) {
        NotificationEntity n = new NotificationEntity();
        n.setId(UUID.randomUUID());
        n.setUserId(RECIPIENT);
        n.setType(type);
        n.setTitle("t");
        n.setPayload(new HashMap<>(payload));
        return n;
    }

    private List<NotificationDto> list(NotificationEntity... rows) {
        when(notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(any(), any()))
                .thenReturn(List.of(rows));
        return service.listNotifications(RECIPIENT, null, 20).items();
    }

    @Test
    void addsEachActorsCurrentAvatarWithOneBatchLookup() {
        when(profileService.findByIds(anyCollection())).thenReturn(List.of(
                new ProfileSearchResultDto(DAVID, "David", "david_official", "https://cdn/avatars/d.webp"),
                new ProfileSearchResultDto(NO_PHOTO, null, "plain", null)));

        List<NotificationDto> items = list(
                row("post_like", Map.of("actor_id", DAVID.toString(), "post_id", "p1")),
                row("dm", Map.of("actor_id", DAVID.toString(), "conversation_id", "c1")),
                row("post_comment", Map.of("actor_id", NO_PHOTO.toString(), "post_id", "p2")));

        assertThat(items.get(0).payload()).containsEntry("actor_avatar_url", "https://cdn/avatars/d.webp");
        assertThat(items.get(1).payload()).containsEntry("actor_avatar_url", "https://cdn/avatars/d.webp");
        assertThat(items.get(2).payload()).doesNotContainKey("actor_avatar_url");

        // The same actor twice is still one id in one query.
        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(profileService).findByIds(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(DAVID, NO_PHOTO);
    }

    @Test
    void replacesAStaleSnapshotWithTheCurrentAvatar() {
        when(profileService.findByIds(anyCollection())).thenReturn(List.of(
                new ProfileSearchResultDto(DAVID, null, "david_official", "https://cdn/avatars/new.webp")));

        List<NotificationDto> items = list(row("post_like", Map.of(
                "actor_id", DAVID.toString(), "actor_avatar_url", "https://cdn/avatars/deleted.webp")));

        assertThat(items.getFirst().payload()).containsEntry("actor_avatar_url", "https://cdn/avatars/new.webp");
    }

    @Test
    void systemRowsAndMalformedIdsSkipTheLookupAndPassThroughUnchanged() {
        List<NotificationDto> items = list(
                row("contest_placed", Map.of("event_id", "e1", "rank", 1)),
                row("post_like", Map.of("actor_id", "not-a-uuid", "post_id", "p1")));

        verify(profileService, never()).findByIds(anyCollection());
        assertThat(items.get(0).payload()).isEqualTo(Map.of("event_id", "e1", "rank", 1));
        assertThat(items.get(1).payload()).doesNotContainKey("actor_avatar_url");
    }
}
