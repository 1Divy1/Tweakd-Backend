package com.tweakdapp.backend.notification.internal.push;

import com.tweakdapp.backend.notification.internal.entities.NotificationEntity;
import com.tweakdapp.backend.notification.internal.entities.UserDeviceEntity;
import com.tweakdapp.backend.notification.internal.repositories.NotificationRepository;
import com.tweakdapp.backend.notification.internal.repositories.UserDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PushDispatcher}: the fan-out to every device of every recipient, the
 * per-recipient badge, pruning of tokens FCM rejected, the {@code dm} skip (those belong to the
 * Supabase edge function), and the guarantee that a push failure never escapes into the producer.
 */
class PushDispatcherTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private NotificationRepository notificationRepository;
    private UserDeviceRepository userDeviceRepository;
    private FcmSender fcmSender;
    private PushDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userDeviceRepository = mock(UserDeviceRepository.class);
        fcmSender = mock(FcmSender.class);
        when(fcmSender.send(anyList())).thenReturn(Set.of());
        dispatcher = new PushDispatcher(notificationRepository, userDeviceRepository, fcmSender);
    }

    private static NotificationEntity notification(UUID id, UUID userId, String type) {
        NotificationEntity entity = new NotificationEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setType(type);
        entity.setTitle("alice liked your post");
        entity.setPayload(Map.of("post_id", "p1"));
        return entity;
    }

    private static UserDeviceEntity device(UUID userId, String token) {
        UserDeviceEntity entity = new UserDeviceEntity();
        entity.setUserId(userId);
        entity.setToken(token);
        return entity;
    }

    private List<PushMessage> capturedMessages() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(fcmSender).send(captor.capture());
        return captor.getValue();
    }

    private void given(List<NotificationEntity> notifications,
                       List<UserDeviceEntity> devices,
                       Object[]... unreadCounts) {
        when(notificationRepository.findAllById(anyCollection())).thenReturn(notifications);
        when(userDeviceRepository.findByUserIdIn(anyCollection())).thenReturn(devices);
        when(notificationRepository.countUnreadGrouped(anyCollection())).thenReturn(List.of(unreadCounts));
    }

    /** One {@code [userId, count]} row as {@code countUnreadGrouped} returns it. */
    private static Object[] unread(UUID userId, long count) {
        return new Object[]{userId, count};
    }

    // ---- fan-out ------------------------------------------------------------

    @Test
    void sendsOneMessagePerDeviceOfTheRecipient() {
        UUID id = UUID.randomUUID();
        given(List.of(notification(id, ALICE, "post_like")),
                List.of(device(ALICE, "tok-1"), device(ALICE, "tok-2")),
                unread(ALICE, 3L));

        dispatcher.on(new NotificationsCreatedEvent(List.of(id)));

        assertThat(capturedMessages())
                .extracting(PushMessage::token)
                .containsExactlyInAnyOrder("tok-1", "tok-2");
    }

    @Test
    void eachRecipientGetsTheirOwnBadgeCount() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        given(List.of(notification(a, ALICE, "post_like"), notification(b, BOB, "post_like")),
                List.of(device(ALICE, "tok-a"), device(BOB, "tok-b")),
                unread(ALICE, 3L), unread(BOB, 11L));

        dispatcher.on(new NotificationsCreatedEvent(List.of(a, b)));

        assertThat(capturedMessages())
                .extracting(PushMessage::token, PushMessage::unreadCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("tok-a", 3L),
                        org.assertj.core.groups.Tuple.tuple("tok-b", 11L));
    }

    @Test
    void aRecipientMissingFromTheCountQueryBadgesZero() {
        UUID id = UUID.randomUUID();
        given(List.of(notification(id, ALICE, "post_like")), List.of(device(ALICE, "tok-1")));

        dispatcher.on(new NotificationsCreatedEvent(List.of(id)));

        assertThat(capturedMessages()).singleElement()
                .extracting(PushMessage::unreadCount).isEqualTo(0L);
    }

    @Test
    void aRecipientWithNoRegisteredDeviceIsSkippedEntirely() {
        UUID id = UUID.randomUUID();
        given(List.of(notification(id, ALICE, "post_like")), List.of());

        dispatcher.on(new NotificationsCreatedEvent(List.of(id)));

        verifyNoInteractions(fcmSender);
    }

    // ---- ownership split ----------------------------------------------------

    /** DM push belongs to the Supabase edge function; Spring must never send it. */
    @Test
    void dmNotificationsAreNotPushedByTheBackend() {
        UUID id = UUID.randomUUID();
        given(List.of(notification(id, ALICE, "dm")), List.of(device(ALICE, "tok-1")));

        dispatcher.on(new NotificationsCreatedEvent(List.of(id)));

        verifyNoInteractions(fcmSender);
    }

    @Test
    void aDmMixedIntoABatchIsDroppedWhileTheRestStillSend() {
        UUID dm = UUID.randomUUID();
        UUID like = UUID.randomUUID();
        given(List.of(notification(dm, ALICE, "dm"), notification(like, ALICE, "post_like")),
                List.of(device(ALICE, "tok-1")),
                unread(ALICE, 1L));

        dispatcher.on(new NotificationsCreatedEvent(List.of(dm, like)));

        assertThat(capturedMessages()).singleElement()
                .extracting(PushMessage::type).isEqualTo("post_like");
    }

    // ---- pruning ------------------------------------------------------------

    @Test
    void tokensReportedDeadByFcmArePruned() {
        UUID id = UUID.randomUUID();
        given(List.of(notification(id, ALICE, "post_like")), List.of(device(ALICE, "tok-1")),
                unread(ALICE, 1L));
        when(fcmSender.send(anyList())).thenReturn(Set.of("tok-1"));

        dispatcher.on(new NotificationsCreatedEvent(List.of(id)));

        verify(userDeviceRepository).deleteByTokenIn(Set.of("tok-1"));
    }

    @Test
    void nothingIsPrunedWhenFcmReportsNoDeadTokens() {
        UUID id = UUID.randomUUID();
        given(List.of(notification(id, ALICE, "post_like")), List.of(device(ALICE, "tok-1")),
                unread(ALICE, 1L));

        dispatcher.on(new NotificationsCreatedEvent(List.of(id)));

        verify(userDeviceRepository, never()).deleteByTokenIn(anyCollection());
    }

    // ---- isolation ----------------------------------------------------------

    /** Push is best effort: a delivery failure must never propagate back to the producer. */
    @Test
    void aFailureInsideDispatchNeverEscapes() {
        UUID id = UUID.randomUUID();
        given(List.of(notification(id, ALICE, "post_like")), List.of(device(ALICE, "tok-1")),
                unread(ALICE, 1L));
        when(fcmSender.send(anyList())).thenThrow(new RuntimeException("FCM exploded"));

        assertThatCode(() -> dispatcher.on(new NotificationsCreatedEvent(List.of(id))))
                .doesNotThrowAnyException();
    }
}
