package com.tweakdapp.backend.presence.internal;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

/**
 * Feeds the registry from the broker's session lifecycle events. The principal name is the JWT
 * subject (the user's UUID) set by {@code shared/realtime/JwtChannelInterceptor}, so an
 * authenticated session always parses; a session that somehow lacks a user is ignored.
 *
 * <p>Lifecycle side effects live in {@link PresenceLifecycle} (a separate bean) so its
 * {@code @Transactional} boundary is a real proxy call, not a self-invocation.
 */
@Component
class PresenceSessionListener {

    private final PresenceRegistry registry;
    private final PresenceLifecycle lifecycle;

    PresenceSessionListener(PresenceRegistry registry, PresenceLifecycle lifecycle) {
        this.registry = registry;
        this.lifecycle = lifecycle;
    }

    @EventListener
    void on(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (user == null || sessionId == null) {
            return;
        }
        UUID userId = UUID.fromString(user.getName());
        if (registry.addSession(userId, sessionId)) {
            lifecycle.markOnline(userId);
        }
    }

    @EventListener
    void on(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user == null) {
            return;
        }
        // The offline transition (if any) is finalized by the sweep after the grace period.
        registry.removeSession(UUID.fromString(user.getName()), event.getSessionId());
    }
}
