package com.tweakdapp.backend.notification.internal.push;

import com.tweakdapp.backend.notification.internal.NotificationServiceImpl;
import com.tweakdapp.backend.notification.internal.entities.NotificationEntity;
import com.tweakdapp.backend.notification.internal.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that writing a notification hands its id to the push pipeline. The dispatcher itself is
 * an {@code @TransactionalEventListener}, so publishing — rather than calling FCM inline — is what
 * guarantees a rolled-back notification is never pushed.
 */
class NotificationPushEventTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    private NotificationRepository notificationRepository;
    private ApplicationEventPublisher eventPublisher;
    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        when(notificationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(notificationRepository.saveAll(any())).thenAnswer(i -> {
            Iterable<?> entities = i.getArgument(0);
            return java.util.stream.StreamSupport.stream(entities.spliterator(), false).toList();
        });
        service = new NotificationServiceImpl(notificationRepository, eventPublisher);
    }

    private NotificationsCreatedEvent capturedEvent() {
        ArgumentCaptor<NotificationsCreatedEvent> captor =
                ArgumentCaptor.forClass(NotificationsCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void pushingOneNotificationPublishesItsIdForDelivery() {
        service.push(ALICE, "post_like", "alice liked your post", null, Map.of("post_id", "p1"));

        assertThat(capturedEvent().notificationIds()).hasSize(1);
    }

    @Test
    void pushToAllPublishesOneEventCarryingEveryId() {
        service.pushToAll(List.of(ALICE, BOB), "feedback_status", "Updated", null, Map.of());

        assertThat(capturedEvent().notificationIds()).hasSize(2);
    }

    @Test
    void deDuplicatedRecipientsProduceOneIdEach() {
        service.pushToAll(List.of(ALICE, ALICE, BOB), "feedback_status", "Updated", null, Map.of());

        assertThat(capturedEvent().notificationIds()).hasSize(2);
    }

    @Test
    void anEmptyRecipientListPublishesNothing() {
        service.pushToAll(List.of(), "feedback_status", "Updated", null, Map.of());

        verify(eventPublisher, never()).publishEvent(any(NotificationsCreatedEvent.class));
    }

    @Test
    void thePublishedIdsMatchTheSavedRows() {
        service.push(ALICE, "post_like", "alice liked your post", null, Map.of());

        ArgumentCaptor<NotificationEntity> saved = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(capturedEvent().notificationIds()).containsExactly(saved.getValue().getId());
    }
}
