package com.carsocialmedia.backend.notification.exception;

import com.carsocialmedia.backend.shared.exception.NotFoundException;

import java.util.UUID;

/** The notification does not exist — or belongs to another user, which is deliberately the same 404. */
public class NotificationNotFoundException extends NotFoundException {

    public NotificationNotFoundException(UUID notificationId) {
        super("Notification not found: " + notificationId);
    }
}
