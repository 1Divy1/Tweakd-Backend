package com.tweakdapp.backend.notification.internal.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * Stands in when Firebase is disabled or its credentials could not be resolved. Drops messages and
 * prunes nothing, so a backend with no Firebase setup still serves in-app notifications normally.
 */
class NoOpFcmSender implements FcmSender {

    private static final Logger log = LoggerFactory.getLogger(NoOpFcmSender.class);

    @Override
    public Set<String> send(List<PushMessage> messages) {
        log.debug("Firebase not configured; dropping {} push message(s)", messages.size());
        return Set.of();
    }
}
