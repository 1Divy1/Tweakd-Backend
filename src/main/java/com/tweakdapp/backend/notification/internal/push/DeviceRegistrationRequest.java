package com.tweakdapp.backend.notification.internal.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Device registration payload. Deliberately carries no user id — the account is always taken from
 * the JWT subject, never from the body, so a caller cannot register a device against someone else's
 * account.
 *
 * <p>Every bound mirrors a CHECK constraint on {@code user_devices_firebase_token}, so a bad request
 * fails as a 400 here rather than as a 500 out of the database.
 */
public record DeviceRegistrationRequest(

        @NotBlank(message = "token is required")
        @Size(min = 32, max = 512, message = "token must be between 32 and 512 characters")
        String token,

        @NotBlank(message = "platform is required")
        @Pattern(regexp = "ios|android", message = "platform must be 'ios' or 'android'")
        String platform,

        @Size(max = 32, message = "appVersion must be at most 32 characters")
        String appVersion,

        @Pattern(regexp = "^[a-zA-Z]{2,3}([-_][a-zA-Z0-9]{2,8}){0,2}$",
                 message = "locale must look like 'en' or 'en-US'")
        @Size(max = 32, message = "locale must be at most 32 characters")
        String locale
) {}
