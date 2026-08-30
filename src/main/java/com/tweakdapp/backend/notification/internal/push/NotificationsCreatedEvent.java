package com.tweakdapp.backend.notification.internal.push;

import java.util.List;
import java.util.UUID;

/**
 * Published by the notification service once rows have been written, and consumed by
 * {@link PushDispatcher} after the transaction commits. Module-internal — push is an implementation
 * detail of {@code notification}, not something other modules trigger.
 *
 * <p>Carries ids rather than entities so the dispatcher, which runs on another thread and in another
 * transaction, reloads them rather than touching detached state.
 *
 * <p>A batch is one event, not one event per row, so a {@code pushToAll} fan-out results in a single
 * grouped unread-count query and a single batched FCM call.
 */
public record NotificationsCreatedEvent(List<UUID> notificationIds) {}
