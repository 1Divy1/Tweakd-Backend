package com.carsocialmedia.backend.presence;

import com.carsocialmedia.backend.presence.dto.PresenceDto;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Online / last-seen status. A user is <em>online</em> while they hold at least one authenticated
 * WebSocket session on the {@code /ws} endpoint (multi-device counted), with a short offline grace
 * period so a flapping mobile connection never flickers publicly. Presence is visible to any
 * authenticated user (all accounts are public).
 *
 * <p>Online state lives in this JVM's memory — consistent with the in-memory STOMP broker; only
 * the {@code last_seen_at} watermark is persisted. Modules that want to <em>react</em> to
 * transitions listen for {@link UserPresenceChangedEvent}.
 */
public interface PresenceService {

    /**
     * Batch presence lookup. Every requested id gets an entry: online users have
     * {@code lastSeenAt == null}; users who were never seen online are offline with
     * {@code lastSeenAt == null} as well.
     */
    Map<UUID, PresenceDto> getPresence(Collection<UUID> userIds);
}
