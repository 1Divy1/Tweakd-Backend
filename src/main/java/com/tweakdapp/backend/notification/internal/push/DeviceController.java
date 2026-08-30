package com.tweakdapp.backend.notification.internal.push;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Registration and removal of the caller's push devices.
 *
 * <p>Three deliberate properties, all security-relevant:
 * <ul>
 *   <li>the account is always {@code jwt.getSubject()} and never anything in the request body, so a
 *       device cannot be registered against another account;</li>
 *   <li>unregister is scoped to the caller in the query itself, so one user cannot cut off another
 *       user's notifications by replaying their token;</li>
 *   <li>no endpoint here ever returns a token, and there is no list-devices endpoint — the registry
 *       is write-only from the outside.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/notifications/devices")
class DeviceController {

    private final PushDeviceService pushDeviceService;

    DeviceController(PushDeviceService pushDeviceService) {
        this.pushDeviceService = pushDeviceService;
    }

    /** Idempotent upsert keyed on the token; re-registering hands the device to the caller. */
    @PostMapping
    public void register(@AuthenticationPrincipal Jwt jwt,
                         @Valid @RequestBody DeviceRegistrationRequest request) {
        pushDeviceService.register(UUID.fromString(jwt.getSubject()), request);
    }

    /**
     * Removes the caller's device. Always 204, whether or not a row matched — the endpoint must not
     * double as an oracle for whether a given token exists.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unregister(@AuthenticationPrincipal Jwt jwt,
                           @Valid @RequestBody DeviceUnregistrationRequest request) {
        pushDeviceService.unregister(UUID.fromString(jwt.getSubject()), request.token());
    }
}
