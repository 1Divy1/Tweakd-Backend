package com.tweakdapp.backend.notification.internal.push;

import java.util.Map;
import java.util.UUID;

/**
 * One notification addressed to one device. The dispatcher produces a {@code PushMessage} per
 * (recipient, token) pair — rather than one multicast per recipient — because the APNs badge differs
 * per user, and a multicast sends a single identical message to every token.
 *
 * @param token the destination device's FCM registration token
 * @param notificationId the {@code notifications} row this mirrors; the client uses it to mark read
 * @param type the producer discriminator the client deep-links on (e.g. {@code post_like})
 * @param payload the notification's type-specific ids; values are stringified before they reach FCM
 * @param unreadCount the recipient's unread notification count, for the app badge
 */
record PushMessage(String token,
                   UUID notificationId,
                   String type,
                   String title,
                   String body,
                   Map<String, Object> payload,
                   long unreadCount) {}
