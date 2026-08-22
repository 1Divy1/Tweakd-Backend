package com.tweakdapp.backend.shared.realtime;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * STOMP-level authentication for the {@code /ws} endpoint, using the very same Supabase JWT
 * validation as the REST layer ({@link JwtDecoder} + the {@link JwtAuthenticationConverter} from
 * {@code SecurityConfig}).
 *
 * <ul>
 *   <li><b>CONNECT</b> must carry {@code Authorization: Bearer <jwt>} as a STOMP native header;
 *       otherwise the frame is rejected and no session is established. The resulting principal's
 *       name is the JWT subject — the user's {@code profiles.id} UUID — which is what user
 *       destinations ({@code convertAndSendToUser}) key on.</li>
 *   <li><b>SUBSCRIBE</b> is restricted to the session's own {@code /user/queue/...} destinations,
 *       so a client can never attach itself to the raw broker queues of another user.</li>
 * </ul>
 */
@Component
public class JwtChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    public JwtChannelInterceptor(JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> checkSubscription(accessor);
            default -> { }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing bearer token on STOMP CONNECT");
        }
        try {
            Jwt jwt = jwtDecoder.decode(header.substring("Bearer ".length()));
            AbstractAuthenticationToken authentication = jwtAuthenticationConverter.convert(jwt);
            accessor.setUser(authentication);
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid bearer token on STOMP CONNECT");
        }
    }

    private void checkSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (accessor.getUser() == null
                || destination == null
                || !destination.startsWith("/user/queue/")) {
            throw new MessageDeliveryException("Subscription not allowed: " + accessor.getDestination());
        }
    }
}
