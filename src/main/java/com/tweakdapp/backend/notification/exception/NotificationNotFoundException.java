package com.tweakdapp.backend.notification.exception;

import com.tweakdapp.backend.shared.exception.NotFoundException;

import java.util.UUID;

/** The notification does not exist — or belongs to another user, which is deliberately the same 404. */
public class NotificationNotFoundException extends NotFoundException {

    public NotificationNotFoundException(UUID notificationId) {
        super("Notification not found: " + notificationId);
    }
}
