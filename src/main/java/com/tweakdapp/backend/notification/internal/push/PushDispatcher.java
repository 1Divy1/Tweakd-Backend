package com.tweakdapp.backend.notification.internal.push;

import com.tweakdapp.backend.notification.internal.entities.NotificationEntity;
import com.tweakdapp.backend.notification.internal.entities.UserDeviceEntity;
import com.tweakdapp.backend.notification.internal.repositories.NotificationRepository;
import com.tweakdapp.backend.notification.internal.repositories.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns freshly written notification rows into FCM pushes.
 *
 * <p>Runs asynchronously in its own transaction after the producing one commits — the same
 * {@code @Async @Transactional(REQUIRES_NEW) @TransactionalEventListener} shape the in-app listeners
 * use — so a rolled-back like or comment can never produce a push for a notification that does not
 * exist.
 *
 * <p>Notification preferences are <em>not</em> re-checked here. The producing listeners already gate
 * on them before calling {@code push}, so a row existing means delivery was approved; re-gating
 * would silently double-apply the rules.
 *
 * <p>{@code dm} notifications are skipped: those are written directly by Postgres (the Flutter
 * client sends DMs through a Supabase RPC that never reaches this backend) and pushed by a Supabase
 * edge function. Rows written outside Spring publish no event, so this is belt-and-braces against a
 * future in-Spring DM producer silently double-pushing.
 */
@Component
class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);

    /** Owned by the Supabase edge function, never by this backend. */
    private static final String EDGE_FUNCTION_OWNED_TYPE = "dm";

    private final NotificationRepository notificationRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final FcmSender fcmSender;

    PushDispatcher(NotificationRepository notificationRepository,
                   UserDeviceRepository userDeviceRepository,
                   FcmSender fcmSender) {
        this.notificationRepository = notificationRepository;
        this.userDeviceRepository = userDeviceRepository;
        this.fcmSender = fcmSender;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(NotificationsCreatedEvent event) {
        try {
            dispatch(event.notificationIds());
        } catch (RuntimeException e) {
            // Push is best effort. The in-app notification is already committed and readable over
            // REST, so a delivery failure must never escape and must never affect the producer.
            log.error("Push dispatch failed for {} notification(s)", event.notificationIds().size(), e);
        }
    }

    private void dispatch(List<UUID> notificationIds) {
        List<NotificationEntity> notifications = notificationRepository.findAllById(notificationIds).stream()
                .filter(n -> !EDGE_FUNCTION_OWNED_TYPE.equals(n.getType()))
                .toList();
        if (notifications.isEmpty()) {
            return;
        }

        Set<UUID> recipients = notifications.stream()
                .map(NotificationEntity::getUserId)
                .collect(Collectors.toSet());

        // One query for every recipient's devices...
        Map<UUID, List<UserDeviceEntity>> devicesByUser = userDeviceRepository.findByUserIdIn(recipients).stream()
                .collect(Collectors.groupingBy(UserDeviceEntity::getUserId));
        if (devicesByUser.isEmpty()) {
            return;
        }

        // ...and one grouped query for every recipient's badge count, rather than one per row.
        Map<UUID, Long> unreadByUser = unreadCounts(recipients);

        List<PushMessage> messages = new ArrayList<>();
        for (NotificationEntity notification : notifications) {
            List<UserDeviceEntity> devices = devicesByUser.get(notification.getUserId());
            if (devices == null) {
                continue;
            }
            long unread = unreadByUser.getOrDefault(notification.getUserId(), 0L);
            for (UserDeviceEntity device : devices) {
                messages.add(new PushMessage(
                        device.getToken(),
                        notification.getId(),
                        notification.getType(),
                        notification.getTitle(),
                        notification.getBody(),
                        notification.getPayload(),
                        unread));
            }
        }
        if (messages.isEmpty()) {
            return;
        }

        Set<String> deadTokens = fcmSender.send(messages);
        if (!deadTokens.isEmpty()) {
            int pruned = userDeviceRepository.deleteByTokenIn(deadTokens);
            log.info("Pruned {} dead device token(s)", pruned);
        }
    }

    private Map<UUID, Long> unreadCounts(Set<UUID> recipients) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : notificationRepository.countUnreadGrouped(recipients)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }
}
