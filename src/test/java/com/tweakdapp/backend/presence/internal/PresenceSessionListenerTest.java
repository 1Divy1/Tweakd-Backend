package com.tweakdapp.backend.presence.internal;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The bridge from broker session-lifecycle events to the registry and lifecycle: a first-session
 * connect marks the user online, a non-transitioning connect does not, and a disconnect only
 * removes the session (leaving any offline transition to the scheduled sweep). Events lacking a
 * principal or session id are ignored. Registry and lifecycle are mocked; real
 * {@code SessionConnected/Disconnect} events are built from STOMP frames as the interceptor does.
 */
class PresenceSessionListenerTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String SESSION = "sess-1";

    private final PresenceRegistry registry = mock(PresenceRegistry.class);
    private final PresenceLifecycle lifecycle = mock(PresenceLifecycle.class);
    private final PresenceSessionListener listener = new PresenceSessionListener(registry, lifecycle);

    private static Principal principal(UUID id) {
        return id::toString; // Principal.getName() == the user's UUID string
    }

    private static Message<byte[]> frameWithSession(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
        if (sessionId != null) {
            accessor.setSessionId(sessionId);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private SessionConnectedEvent connectedEvent(String sessionId, Principal user) {
        return new SessionConnectedEvent(this, frameWithSession(sessionId), user);
    }

    private SessionDisconnectEvent disconnectEvent(String sessionId, Principal user) {
        return new SessionDisconnectEvent(this, frameWithSession(sessionId), sessionId, CloseStatus.NORMAL, user);
    }

    @Test
    void connectRegistersTheSessionAndMarksOnlineOnTheFirstSession() {
        when(registry.addSession(USER, SESSION)).thenReturn(true);

        listener.on(connectedEvent(SESSION, principal(USER)));

        verify(registry).addSession(USER, SESSION);
        verify(lifecycle).markOnline(USER);
    }

    @Test
    void connectDoesNotMarkOnlineWhenItIsNotAPublicTransition() {
        when(registry.addSession(USER, SESSION)).thenReturn(false);

        listener.on(connectedEvent(SESSION, principal(USER)));

        verify(registry).addSession(USER, SESSION);
        verify(lifecycle, never()).markOnline(USER);
    }

    @Test
    void connectWithoutAPrincipalIsIgnored() {
        listener.on(connectedEvent(SESSION, null));

        verifyNoInteractions(registry, lifecycle);
    }

    @Test
    void connectWithoutASessionIdIsIgnored() {
        listener.on(connectedEvent(null, principal(USER)));

        verifyNoInteractions(registry, lifecycle);
    }

    @Test
    void disconnectRemovesTheSessionAndDefersAnyOfflineToTheSweep() {
        listener.on(disconnectEvent(SESSION, principal(USER)));

        verify(registry).removeSession(USER, SESSION);
        verifyNoInteractions(lifecycle);
    }

    @Test
    void disconnectWithoutAPrincipalIsIgnored() {
        listener.on(disconnectEvent(SESSION, null));

        verifyNoInteractions(registry, lifecycle);
    }
}
