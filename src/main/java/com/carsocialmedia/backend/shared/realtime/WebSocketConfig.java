package com.carsocialmedia.backend.shared.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * The realtime push channel: a single STOMP-over-WebSocket endpoint at {@code /ws} that any module
 * can push user-targeted events through (currently the dms module; notifications could adopt it
 * later). Clients never talk to Supabase Realtime — the backend is the realtime gateway, so every
 * event passes the same authorization checks as REST.
 *
 * <p>Protocol: the client connects to {@code /ws} with its Supabase JWT (validated at the CONNECT
 * frame by {@link JwtChannelInterceptor} — the HTTP handshake itself is {@code permitAll}) and
 * subscribes to its private queue {@code /user/queue/...}. Modules push with
 * {@code SimpMessagingTemplate.convertAndSendToUser(userId, "/queue/...", event)}. Client-to-server
 * messages (e.g. typing indicators) go to {@code /app/...} destinations handled by
 * {@code @MessageMapping} controllers.
 *
 * <p>The in-memory simple broker is deliberate: the backend runs as a single instance, so events
 * only ever need to reach sessions on this JVM. If the app ever scales horizontally, swap this for
 * a broker relay (Redis/RabbitMQ) — nothing about the REST/DB design changes.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    public WebSocketConfig(JwtChannelInterceptor jwtChannelInterceptor) {
        this.jwtChannelInterceptor = jwtChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Mobile clients send no Origin header; the pattern keeps web-based debugging tools usable.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}
