package com.tweakdapp.backend.notification.internal.push;

import com.tweakdapp.backend.notification.internal.repositories.UserDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PushDeviceServiceImpl}: the device cap that stops a valid JWT growing the
 * registry without bound, blank-to-null normalisation, and the owner-scoped unregister.
 */
class PushDeviceServiceImplTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final String TOKEN = "t".repeat(64);

    private UserDeviceRepository userDeviceRepository;
    private PushDeviceServiceImpl service;

    @BeforeEach
    void setUp() {
        userDeviceRepository = mock(UserDeviceRepository.class);
        when(userDeviceRepository.findIdsBeyondCap(any(), anyInt())).thenReturn(List.of());
        service = new PushDeviceServiceImpl(userDeviceRepository);
    }

    private static DeviceRegistrationRequest request(String appVersion, String locale) {
        return new DeviceRegistrationRequest(TOKEN, "ios", appVersion, locale);
    }

    @Test
    void registerUpsertsWithTheJwtDerivedUser() {
        service.register(USER, request("1.0.0+1", "en"));

        verify(userDeviceRepository).upsert(any(), eq(USER), eq(TOKEN), eq("ios"), eq("1.0.0+1"), eq("en"));
    }

    @Test
    void blankOptionalFieldsAreStoredAsNull() {
        service.register(USER, request("   ", ""));

        verify(userDeviceRepository).upsert(any(), eq(USER), eq(TOKEN), eq("ios"), eq(null), eq(null));
    }

    @Test
    void devicesBeyondTheCapAreEvicted() {
        List<UUID> excess = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(userDeviceRepository.findIdsBeyondCap(eq(USER), anyInt())).thenReturn(excess);

        service.register(USER, request(null, null));

        verify(userDeviceRepository).deleteAllByIdInBatch(excess);
    }

    @Test
    void nothingIsEvictedWhenTheUserIsUnderTheCap() {
        service.register(USER, request(null, null));

        verify(userDeviceRepository, never()).deleteAllByIdInBatch(anyList());
    }

    @Test
    void theCapIsAppliedPerUserAndIsPositive() {
        service.register(USER, request(null, null));

        ArgumentCaptor<Integer> cap = ArgumentCaptor.forClass(Integer.class);
        verify(userDeviceRepository).findIdsBeyondCap(eq(USER), cap.capture());
        assertThat(cap.getValue()).isPositive();
    }

    @Test
    void unregisterIsScopedToTheCallingUser() {
        service.unregister(USER, TOKEN);

        verify(userDeviceRepository).deleteByTokenAndUserId(TOKEN, USER);
    }
}
