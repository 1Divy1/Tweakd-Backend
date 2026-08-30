package com.tweakdapp.backend.dms.internal;

import com.tweakdapp.backend.dms.internal.repositories.DmConversationRepository;
import com.tweakdapp.backend.presence.UserPresenceChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Fans a user's presence flips out to their DM peers' queues, so open chat screens flip
 * "Active now" live. Transitions are already debounced by the presence module's grace window, so
 * this fires rarely; users without conversations cost one indexed query and zero sends.
 */
@Component
class DmPresencePusher {

    private final DmConversationRepository conversationRepository;
    private final DmEventPusher eventPusher;

    DmPresencePusher(DmConversationRepository conversationRepository, DmEventPusher eventPusher) {
        this.conversationRepository = conversationRepository;
        this.eventPusher = eventPusher;
    }

    @EventListener
    void on(UserPresenceChangedEvent event) {
        List<UUID> peerIds = conversationRepository.findPeerIdsOf(event.userId());
        if (!peerIds.isEmpty()) {
            eventPusher.pushPresence(peerIds, event.userId(), event.online(), event.lastSeenAt());
        }
    }
}
