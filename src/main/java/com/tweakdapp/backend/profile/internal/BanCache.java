package com.tweakdapp.backend.profile.internal;

import com.tweakdapp.backend.profile.internal.repository.ProfileRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user ban state with a short TTL, so {@link BannedUserInterceptor} doesn't hit the DB on every
 * request. A moderator ban therefore takes effect within {@code TTL} at worst — except on this
 * instance, where {@link #evict} makes it immediate. A temp ban whose {@code banned_until} has
 * passed reads as not banned (the flag is left for the next moderator sweep / unban).
 */
@Component
class BanCache {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final ProfileRepository profileRepository;
    private final ConcurrentHashMap<UUID, Entry> cache = new ConcurrentHashMap<>();

    BanCache(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    boolean isBanned(UUID userId) {
        Entry entry = cache.compute(userId, (id, cached) ->
                cached != null && !cached.expired() ? cached : load(id));
        return entry.banned();
    }

    /** Called on ban / unban so enforcement on this instance is immediate. */
    void evict(UUID userId) {
        cache.remove(userId);
    }

    private Entry load(UUID userId) {
        boolean banned = profileRepository.findBanState(userId)
                .map(state -> state.getBanned()
                        && (state.getBannedUntil() == null || state.getBannedUntil().isAfter(Instant.now())))
                .orElse(false);
        return new Entry(banned, Instant.now().plus(TTL));
    }

    private record Entry(boolean banned, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
