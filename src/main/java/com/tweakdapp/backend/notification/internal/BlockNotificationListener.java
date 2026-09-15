package com.tweakdapp.backend.notification.internal;

import com.tweakdapp.backend.notification.internal.repositories.NotificationRepository;
import com.tweakdapp.backend.shared.blocking.UserBlockedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Deletes every notification one side of a new block received about the other (likes, comments,
 * follows, DMs, …), in both directions. A block hides the other account everywhere; a leftover
 * "x liked your post" row would still name them and deep-link into content that now 404s.
 * Future notifications between the pair are refused at write time by
 * {@link NotificationServiceImpl}.
 */
@Component
class BlockNotificationListener {

    private final NotificationRepository notificationRepository;

    BlockNotificationListener(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(UserBlockedEvent event) {
        notificationRepository.deleteBetween(event.blockerId(), event.blockedId());
    }
}
