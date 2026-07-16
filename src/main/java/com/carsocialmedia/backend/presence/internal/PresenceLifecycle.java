package com.carsocialmedia.backend.presence.internal;

import com.carsocialmedia.backend.presence.UserPresenceChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Turns registry transitions into the durable side effects: the {@code last_seen_at} watermark and
 * {@link UserPresenceChangedEvent}s. The DB is only ever touched here — on the rare public
 * transitions and a cheap periodic batch flush — never per WebSocket frame, so presence adds no
 * meaningful load however chatty the sockets are.
 */
@Component
class PresenceLifecycle {

    /**
     * How long a user with zero sessions keeps counting as online. Absorbs mobile network flaps
     * and app-switches entirely: a reconnect inside this window causes no DB write and no fan-out.
     */
    static final Duration OFFLINE_GRACE = Duration.ofSeconds(20);

    private final PresenceRegistry registry;
    private final UserPresenceRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    PresenceLifecycle(PresenceRegistry registry,
                      UserPresenceRepository repository,
                      ApplicationEventPublisher eventPublisher) {
        this.registry = registry;
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void markOnline(UUID userId) {
        repository.upsertLastSeen(userId, Instant.now());
        eventPublisher.publishEvent(new UserPresenceChangedEvent(userId, true, null));
    }

    /** Finalizes users whose offline grace window elapsed without a reconnect. */
    @Scheduled(fixedDelay = 10_000)
    @Transactional
    public void sweepOffline() {
        List<UUID> expired = registry.sweepExpired(OFFLINE_GRACE);
        Instant now = Instant.now();
        for (UUID userId : expired) {
            repository.upsertLastSeen(userId, now);
            eventPublisher.publishEvent(new UserPresenceChangedEvent(userId, false, now));
        }
    }

    /**
     * Keeps online users' watermarks fresh so a server restart (which loses the in-memory
     * registry) shows them as "last seen a couple of minutes ago" instead of hours stale.
     */
    @Scheduled(fixedDelay = 120_000)
    @Transactional
    public void flushOnlineWatermarks() {
        Set<UUID> online = registry.onlineUsers();
        if (!online.isEmpty()) {
            repository.touchAll(online, Instant.now());
        }
    }
}
