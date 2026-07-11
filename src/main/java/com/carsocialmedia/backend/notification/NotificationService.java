package com.carsocialmedia.backend.notification;

import com.carsocialmedia.backend.notification.dto.NotificationDto;
import com.carsocialmedia.backend.notification.dto.NotificationPageDto;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface NotificationService {

    /**
     * Stores one in-app notification for a user. This is the single write entry point other modules
     * use to notify someone (feedback status changes, support ticket replies, moderation warnings...).
     *
     * @param userId the recipient's profile UUID
     * @param type producer discriminator (e.g. {@code feedback_status}, {@code ticket_reply},
     *        {@code moderation_warning}, {@code content_removed}) — the client switches its
     *        deep-link/rendering on it
     * @param title short headline shown in the notification list
     * @param body optional longer text, may be {@code null}
     * @param payload type-specific ids for deep-linking (e.g. {@code feedbackId}); never {@code null},
     *        pass an empty map when there is nothing to link
     */
    void push(UUID userId, String type, String title, String body, Map<String, Object> payload);

    /**
     * Stores the same notification for many users at once (e.g. every subscriber of a feedback).
     * Recipients are de-duplicated; an empty collection is a no-op.
     */
    void pushToAll(Collection<UUID> userIds, String type, String title, String body, Map<String, Object> payload);

    /**
     * One keyset page of the user's notifications, newest first.
     *
     * @param userId the recipient (JWT subject)
     * @param cursor opaque cursor from the previous page, or {@code null} for the first page
     * @param size max notifications to return (clamped to a sane maximum)
     */
    NotificationPageDto listNotifications(UUID userId, String cursor, int size);

    /** Number of unread notifications, for the app's badge. */
    long countUnread(UUID userId);

    /**
     * Marks one notification as read.
     *
     * @throws com.carsocialmedia.backend.notification.exception.NotificationNotFoundException if the
     *         notification does not exist or belongs to another user
     */
    NotificationDto markRead(UUID userId, UUID notificationId);

    /** Marks every unread notification of the user as read. Returns how many were affected. */
    int markAllRead(UUID userId);
}
