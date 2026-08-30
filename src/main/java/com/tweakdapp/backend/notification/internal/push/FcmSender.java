package com.tweakdapp.backend.notification.internal.push;

import java.util.List;
import java.util.Set;

/**
 * Delivers push messages to FCM. An interface so the dispatcher can be tested without Firebase, and
 * so a missing credential degrades to {@link NoOpFcmSender} rather than breaking startup.
 */
interface FcmSender {

    /**
     * Sends every message, best effort.
     *
     * @return the tokens FCM reported as permanently dead, for the caller to prune. Transient
     *         failures are not included — those devices are still valid and must be kept.
     */
    Set<String> send(List<PushMessage> messages);
}
