package com.tweakdapp.backend.notification.internal.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Unregister payload. The token travels in the request body rather than the URL path on purpose: a
 * path segment is recorded verbatim in access, proxy and CDN logs, and an FCM token is a
 * device-addressable secret — anyone holding it can push arbitrary notifications to that device.
 */
public record DeviceUnregistrationRequest(

        @NotBlank(message = "token is required")
        @Size(min = 32, max = 512, message = "token must be between 32 and 512 characters")
        String token
) {}
