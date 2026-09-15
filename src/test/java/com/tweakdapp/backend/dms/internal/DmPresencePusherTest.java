package com.tweakdapp.backend.dms.internal;

import com.tweakdapp.backend.dms.internal.repositories.DmConversationRepository;
import com.tweakdapp.backend.presence.UserPresenceChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.tweakdapp.backend.profile.ProfileService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The presence→DM bridge ({@link DmPresencePusher}): a user's presence flip fans out to their DM
 * peers' queues, and users with no message-carrying conversation cost zero sends.
 */
class DmPresencePusherTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private DmConversationRepository conversationRepository;
    private DmEventPusher eventPusher;
    private DmPresencePusher pusher;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(DmConversationRepository.class);
        eventPusher = mock(DmEventPusher.class);
        pusher = new DmPresencePusher(conversationRepository, eventPusher, mock(ProfileService.class));
    }

    @Test
    void fansThePresenceFlipOutToAllDmPeers() {
        UUID peer1 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID peer2 = UUID.fromString("00000000-0000-0000-0000-000000000003");
        Instant lastSeen = Instant.parse("2026-07-16T10:00:00Z");
        when(conversationRepository.findPeerIdsOf(USER)).thenReturn(List.of(peer1, peer2));

        pusher.on(new UserPresenceChangedEvent(USER, false, lastSeen));

        verify(eventPusher).pushPresence(List.of(peer1, peer2), USER, false, lastSeen);
    }

    @Test
    void aUserWithNoConversationsTriggersNoSends() {
        when(conversationRepository.findPeerIdsOf(USER)).thenReturn(List.of());

        pusher.on(new UserPresenceChangedEvent(USER, true, null));

        verifyNoInteractions(eventPusher);
    }
}
