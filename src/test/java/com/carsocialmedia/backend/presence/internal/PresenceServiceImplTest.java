package com.carsocialmedia.backend.presence.internal;

import com.carsocialmedia.backend.presence.dto.PresenceDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The batch lookup that fuses in-memory online state with persisted last-seen watermarks: online
 * users report {@code online=true} with a null last-seen and never hit the DB, offline users read
 * their watermark (null when never seen), only the offline ids are queried, and every requested id
 * — duplicates collapsed — gets exactly one entry (the contract the dms hydration relies on).
 */
class PresenceServiceImplTest {

    private static final UUID ONLINE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SEEN = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID NEVER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant LAST_SEEN = Instant.parse("2026-07-16T10:00:00Z");

    private final PresenceRegistry registry = mock(PresenceRegistry.class);
    private final UserPresenceRepository repository = mock(UserPresenceRepository.class);
    private final PresenceServiceImpl service = new PresenceServiceImpl(registry, repository);

    private static UserPresenceEntity entity(UUID id, Instant lastSeen) {
        UserPresenceEntity e = new UserPresenceEntity();
        e.setUserId(id);
        e.setLastSeenAt(lastSeen);
        return e;
    }

    @Test
    void onlineUserIsReportedOnlineWithoutHittingTheDb() {
        when(registry.isOnline(ONLINE)).thenReturn(true);

        Map<UUID, PresenceDto> result = service.getPresence(List.of(ONLINE));

        assertThat(result.get(ONLINE)).isEqualTo(PresenceDto.online(ONLINE));
        verify(repository, never()).findAllById(anyIterable());
    }

    @Test
    void offlineUserWithAWatermarkReportsLastSeen() {
        when(registry.isOnline(SEEN)).thenReturn(false);
        when(repository.findAllById(List.of(SEEN))).thenReturn(List.of(entity(SEEN, LAST_SEEN)));

        Map<UUID, PresenceDto> result = service.getPresence(List.of(SEEN));

        assertThat(result.get(SEEN)).isEqualTo(PresenceDto.offline(SEEN, LAST_SEEN));
    }

    @Test
    void offlineUserNeverSeenReportsANullLastSeen() {
        when(registry.isOnline(NEVER)).thenReturn(false);
        when(repository.findAllById(List.of(NEVER))).thenReturn(List.of());

        Map<UUID, PresenceDto> result = service.getPresence(List.of(NEVER));

        assertThat(result.get(NEVER)).isEqualTo(PresenceDto.offline(NEVER, null));
    }

    @Test
    void onlyOfflineIdsAreQueriedAgainstTheDb() {
        when(registry.isOnline(ONLINE)).thenReturn(true);
        when(registry.isOnline(SEEN)).thenReturn(false);
        when(repository.findAllById(List.of(SEEN))).thenReturn(List.of(entity(SEEN, LAST_SEEN)));

        Map<UUID, PresenceDto> result = service.getPresence(List.of(ONLINE, SEEN));

        verify(repository).findAllById(List.of(SEEN));
        assertThat(result.get(ONLINE).online()).isTrue();
        assertThat(result.get(SEEN).online()).isFalse();
    }

    @Test
    void everyRequestedIdGetsAnEntry() {
        when(registry.isOnline(ONLINE)).thenReturn(true);
        when(registry.isOnline(SEEN)).thenReturn(false);
        when(registry.isOnline(NEVER)).thenReturn(false);
        when(repository.findAllById(List.of(SEEN, NEVER))).thenReturn(List.of(entity(SEEN, LAST_SEEN)));

        Map<UUID, PresenceDto> result = service.getPresence(List.of(ONLINE, SEEN, NEVER));

        assertThat(result).containsOnlyKeys(ONLINE, SEEN, NEVER);
    }

    @Test
    void duplicateIdsAreCollapsedToASingleEntryAndQueriedOnce() {
        when(registry.isOnline(SEEN)).thenReturn(false);
        when(repository.findAllById(List.of(SEEN))).thenReturn(List.of(entity(SEEN, LAST_SEEN)));

        Map<UUID, PresenceDto> result = service.getPresence(List.of(SEEN, SEEN, SEEN));

        assertThat(result).containsOnlyKeys(SEEN);
        verify(repository).findAllById(List.of(SEEN));
    }
}
