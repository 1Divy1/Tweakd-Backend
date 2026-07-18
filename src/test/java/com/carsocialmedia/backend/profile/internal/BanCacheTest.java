package com.carsocialmedia.backend.profile.internal;

import com.carsocialmedia.backend.profile.internal.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BanCacheTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private ProfileRepository profileRepository;
    private BanCache banCache;

    @BeforeEach
    void setUp() {
        profileRepository = mock(ProfileRepository.class);
        banCache = new BanCache(profileRepository);
    }

    private void banState(boolean banned, Instant bannedUntil) {
        ProfileRepository.BanState state = mock(ProfileRepository.BanState.class);
        when(state.getBanned()).thenReturn(banned);
        when(state.getBannedUntil()).thenReturn(bannedUntil);
        when(profileRepository.findBanState(USER_ID)).thenReturn(Optional.of(state));
    }

    @Test
    void missingProfileReadsAsNotBanned() {
        when(profileRepository.findBanState(USER_ID)).thenReturn(Optional.empty());

        assertThat(banCache.isBanned(USER_ID)).isFalse();
    }

    @Test
    void permanentBanReadsAsBanned() {
        banState(true, null);

        assertThat(banCache.isBanned(USER_ID)).isTrue();
    }

    @Test
    void tempBanStillActiveReadsAsBanned() {
        banState(true, Instant.now().plusSeconds(3600));

        assertThat(banCache.isBanned(USER_ID)).isTrue();
    }

    @Test
    void expiredTempBanReadsAsNotBanned() {
        banState(true, Instant.now().minusSeconds(1));

        assertThat(banCache.isBanned(USER_ID)).isFalse();
    }

    @Test
    void unbannedUserReadsAsNotBanned() {
        banState(false, null);

        assertThat(banCache.isBanned(USER_ID)).isFalse();
    }

    @Test
    void secondLookupWithinTtlIsServedFromCache() {
        banState(true, null);

        banCache.isBanned(USER_ID);
        banCache.isBanned(USER_ID);

        verify(profileRepository, times(1)).findBanState(USER_ID);
    }

    @Test
    void evictForcesAFreshLookup() {
        banState(true, null);

        banCache.isBanned(USER_ID);
        banCache.evict(USER_ID);
        banCache.isBanned(USER_ID);

        verify(profileRepository, times(2)).findBanState(USER_ID);
    }
}
