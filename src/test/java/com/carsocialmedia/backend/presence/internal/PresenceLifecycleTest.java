package com.carsocialmedia.backend.presence.internal;

import com.carsocialmedia.backend.presence.UserPresenceChangedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The durable side effects of presence transitions: {@code last_seen_at} upserts and
 * {@link UserPresenceChangedEvent}s. The registry, repository and event publisher are mocked so
 * this exercises only the lifecycle's own logic — that online marks publish {@code online=true}
 * with a null last-seen, that the sweep finalizes exactly the users the registry expires (with the
 * 20s grace) publishing {@code online=false} with the persisted watermark, and that the periodic
 * flush skips the DB when nobody is online.
 */
class PresenceLifecycleTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final PresenceRegistry registry = mock(PresenceRegistry.class);
    private final UserPresenceRepository repository = mock(UserPresenceRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final PresenceLifecycle lifecycle = new PresenceLifecycle(registry, repository, eventPublisher);

    @Test
    void graceWindowIsTwentySeconds() {
        assertThat(PresenceLifecycle.OFFLINE_GRACE).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    void markOnlineUpsertsTheWatermarkAndPublishesAnOnlineEvent() {
        lifecycle.markOnline(USER);

        verify(repository).upsertLastSeen(eq(USER), any(Instant.class));
        ArgumentCaptor<UserPresenceChangedEvent> event = ArgumentCaptor.forClass(UserPresenceChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(USER);
        assertThat(event.getValue().online()).isTrue();
        assertThat(event.getValue().lastSeenAt()).isNull();
    }

    @Test
    void sweepOfflineUsesTheTwentySecondGraceAndFinalizesEveryExpiredUser() {
        when(registry.sweepExpired(PresenceLifecycle.OFFLINE_GRACE)).thenReturn(List.of(USER, OTHER));

        lifecycle.sweepOffline();

        verify(registry).sweepExpired(PresenceLifecycle.OFFLINE_GRACE);
        verify(repository).upsertLastSeen(eq(USER), any(Instant.class));
        verify(repository).upsertLastSeen(eq(OTHER), any(Instant.class));
        ArgumentCaptor<UserPresenceChangedEvent> events = ArgumentCaptor.forClass(UserPresenceChangedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues())
                .allSatisfy(e -> assertThat(e.online()).isFalse())
                .allSatisfy(e -> assertThat(e.lastSeenAt()).isNotNull());
        assertThat(events.getAllValues()).extracting(UserPresenceChangedEvent::userId)
                .containsExactly(USER, OTHER);
    }

    @Test
    void offlineEventCarriesTheSameInstantThatWasPersisted() {
        when(registry.sweepExpired(PresenceLifecycle.OFFLINE_GRACE)).thenReturn(List.of(USER));

        lifecycle.sweepOffline();

        ArgumentCaptor<Instant> persisted = ArgumentCaptor.forClass(Instant.class);
        verify(repository).upsertLastSeen(eq(USER), persisted.capture());
        ArgumentCaptor<UserPresenceChangedEvent> event = ArgumentCaptor.forClass(UserPresenceChangedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().lastSeenAt()).isEqualTo(persisted.getValue());
    }

    @Test
    void sweepOfflineWithNothingExpiredTouchesNeitherDbNorEventBus() {
        when(registry.sweepExpired(PresenceLifecycle.OFFLINE_GRACE)).thenReturn(List.of());

        lifecycle.sweepOffline();

        verifyNoInteractions(repository);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void flushBatchTouchesTheWatermarkOfEveryOnlineUser() {
        Set<UUID> online = Set.of(USER, OTHER);
        when(registry.onlineUsers()).thenReturn(online);

        lifecycle.flushOnlineWatermarks();

        verify(repository).touchAll(eq(online), any(Instant.class));
    }

    @Test
    void flushSkipsTheDbEntirelyWhenNobodyIsOnline() {
        when(registry.onlineUsers()).thenReturn(Set.of());

        lifecycle.flushOnlineWatermarks();

        verify(repository, never()).touchAll(any(), any());
    }
}
