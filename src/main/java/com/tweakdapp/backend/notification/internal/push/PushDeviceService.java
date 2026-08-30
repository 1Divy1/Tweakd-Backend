package com.tweakdapp.backend.notification.internal.push;

import java.util.UUID;

/**
 * The device registry behind push delivery. Module-internal: nothing outside {@code notification}
 * registers devices — the app talks to {@code DeviceController} directly.
 */
interface PushDeviceService {

    /**
     * Idempotently registers a device for a user. Re-registering a token already held by another
     * account reassigns it (device handoff). Enforces the per-user device cap.
     */
    void register(UUID userId, DeviceRegistrationRequest request);

    /** Removes the caller's device. A token the caller does not own is left untouched. */
    void unregister(UUID userId, String token);
}
