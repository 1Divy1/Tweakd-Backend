package com.carsocialmedia.backend.profile.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BannedUserInterceptorTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private BanCache banCache;
    private BannedUserInterceptor interceptor;
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        banCache = mock(BanCache.class);
        interceptor = new BannedUserInterceptor(banCache, new ObjectMapper());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void unauthenticatedRequestsPassThrough() throws Exception {
        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void nonJwtPrincipalsPassThrough() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("someone", "credentials"));

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void nonUuidSubjectPassesThrough() throws Exception {
        authenticateAs("not-a-user-uuid");

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void unbannedUserPassesThrough() throws Exception {
        authenticateAs(USER_ID.toString());
        when(banCache.isBanned(USER_ID)).thenReturn(false);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @Test
    void bannedUserIsRejectedWith403ErrorResponse() throws Exception {
        authenticateAs(USER_ID.toString());
        when(banCache.isBanned(USER_ID)).thenReturn(true);

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("Your account has been banned");
    }
}
