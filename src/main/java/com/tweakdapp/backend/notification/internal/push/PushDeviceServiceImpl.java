package com.tweakdapp.backend.notification.internal.push;

import com.tweakdapp.backend.notification.internal.repositories.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class PushDeviceServiceImpl implements PushDeviceService {

    private static final Logger log = LoggerFactory.getLogger(PushDeviceServiceImpl.class);

    /**
     * Most devices one account is expected to hold. Beyond this the oldest registrations are
     * evicted, so a valid JWT cannot be replayed to grow the table without bound.
     */
    private static final int MAX_DEVICES_PER_USER = 10;

    private final UserDeviceRepository userDeviceRepository;

    PushDeviceServiceImpl(UserDeviceRepository userDeviceRepository) {
        this.userDeviceRepository = userDeviceRepository;
    }

    @Override
    @Transactional
    public void register(UUID userId, DeviceRegistrationRequest request) {
        userDeviceRepository.upsert(
                UUID.randomUUID(),
                userId,
                request.token(),
                request.platform(),
                blankToNull(request.appVersion()),
                blankToNull(request.locale()));

        List<UUID> excess = userDeviceRepository.findIdsBeyondCap(userId, MAX_DEVICES_PER_USER);
        if (!excess.isEmpty()) {
            userDeviceRepository.deleteAllByIdInBatch(excess);
            log.info("Evicted {} device(s) over the cap for user {}", excess.size(), userId);
        }
    }

    @Override
    @Transactional
    public void unregister(UUID userId, String token) {
        int removed = userDeviceRepository.deleteByTokenAndUserId(token, userId);
        // Logged at debug and without the token: a miss is the normal idempotent case (the app
        // retried, or the token was already reassigned to another account by a handoff).
        log.debug("Unregister for user {} removed {} row(s)", userId, removed);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
