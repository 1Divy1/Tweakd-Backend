package com.tweakdapp.backend.shared.realtime;

import com.tweakdapp.backend.shared.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * STOMP-frame authentication rules: CONNECT requires a valid bearer token, SUBSCRIBE is
 * restricted to the session's own {@code /user/queue/...} destinations.
 */
class JwtChannelInterceptorTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    private JwtDecoder jwtDecoder;
    private JwtChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        jwtDecoder = mock(JwtDecoder.class);
        // The real converter from SecurityConfig, so STOMP and REST role mapping can't drift apart.
        interceptor = new JwtChannelInterceptor(jwtDecoder, new SecurityConfig().jwtAuthenticationConverter());
    }

    private static Message<byte[]> connectFrame(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorizationHeader != null) {
            accessor.addNativeHeader("Authorization", authorizationHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> subscribeFrame(String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connectWithoutAuthorizationHeaderIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(connectFrame(null), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Missing bearer token");
    }

    @Test
    void connectWithNonBearerHeaderIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(connectFrame("Basic dXNlcjpwdw=="), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Missing bearer token");
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        when(jwtDecoder.decode("bad-token")).thenThrow(new JwtException("expired"));

        assertThatThrownBy(() -> interceptor.preSend(connectFrame("Bearer bad-token"), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Invalid bearer token");
    }

    @Test
    void connectWithValidTokenAuthenticatesTheSessionAsTheJwtSubject() {
        Jwt jwt = Jwt.withTokenValue("good-token")
                .header("alg", "none")
                .subject(USER_ID)
                .build();
        when(jwtDecoder.decode("good-token")).thenReturn(jwt);

        Message<?> result = interceptor.preSend(connectFrame("Bearer good-token"), channel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(USER_ID);
    }

    @Test
    void subscribeToOwnUserQueueIsAllowed() {
        Message<byte[]> frame = subscribeFrame("/user/queue/dms", () -> USER_ID);

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
    }

    @Test
    void subscribeWithoutAuthenticatedSessionIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(subscribeFrame("/user/queue/dms", null), channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Subscription not allowed");
    }

    @Test
    void subscribeOutsideUserQueueIsRejected() {
        Message<byte[]> frame = subscribeFrame("/queue/dms-user123", () -> USER_ID);

        assertThatThrownBy(() -> interceptor.preSend(frame, channel))
                .isInstanceOf(MessageDeliveryException.class)
                .hasMessageContaining("Subscription not allowed");
    }

    @Test
    void nonStompMessagesPassThroughUntouched() {
        Message<byte[]> plain = MessageBuilder.withPayload(new byte[0]).build();

        assertThat(interceptor.preSend(plain, channel)).isSameAs(plain);
    }
}
