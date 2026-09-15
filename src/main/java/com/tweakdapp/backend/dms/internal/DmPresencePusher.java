package com.tweakdapp.backend.dms.internal;

import com.tweakdapp.backend.dms.internal.repositories.DmConversationRepository;
import com.tweakdapp.backend.presence.UserPresenceChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import com.tweakdapp.backend.profile.ProfileService;
import java.util.Set;

/**
 * Fans a user's presence flips out to their DM peers' queues, so open chat screens flip
 * "Active now" live. Transitions are already debounced by the presence module's grace window, so
 * this fires rarely; users without conversations cost one indexed query and zero sends.
 */
@Component
class DmPresencePusher {

    private final DmConversationRepository conversationRepository;
    private final DmEventPusher eventPusher;
    private final ProfileService profileService;

    DmPresencePusher(DmConversationRepository conversationRepository, DmEventPusher eventPusher,
                     ProfileService profileService) {
        this.conversationRepository = conversationRepository;
        this.eventPusher = eventPusher;
        this.profileService = profileService;
    }

    @EventListener
    void on(UserPresenceChangedEvent event) {
        // A blocked pair does not see each other's "Active now".
        Set<UUID> hidden = profileService.findHiddenProfileIds(event.userId());
        List<UUID> peerIds = conversationRepository.findPeerIdsOf(event.userId()).stream()
                .filter(peerId -> !hidden.contains(peerId))
                .toList();
        if (!peerIds.isEmpty()) {
            eventPusher.pushPresence(peerIds, event.userId(), event.online(), event.lastSeenAt());
        }
    }
}
