package com.carsocialmedia.backend.testsupport;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Fabricated Supabase-style JWTs for {@code @WebMvcTest} / MockMvc tests.
 *
 * <p>These bypass signature validation entirely (no network, no real tokens). The subject
 * is the user's Supabase UUID — exactly what controllers read via {@code jwt.getSubject()}.
 *
 * <p>Usage: {@code mockMvc.perform(get("/api/v1/...").with(TestJwts.user()))}.
 */
public final class TestJwts {

    /** Default authenticated app user for tests that don't care about the exact ID. */
    public static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    /** Default admin/staff user. Staff have no profiles row — mirror that in fixtures. */
    public static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private TestJwts() {
    }

    public static JwtRequestPostProcessor user() {
        return user(USER_ID);
    }

    /** Regular app user: no app_metadata.role claim, which maps to ROLE_USER. */
    public static JwtRequestPostProcessor user(UUID userId) {
        return jwt()
                .jwt(j -> j.subject(userId.toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    public static JwtRequestPostProcessor admin() {
        return admin(ADMIN_ID);
    }

    /** Staff user: app_metadata.role = admin, which maps to ROLE_ADMIN. */
    public static JwtRequestPostProcessor admin(UUID userId) {
        return jwt()
                .jwt(j -> j.subject(userId.toString())
                        .claim("app_metadata", Map.of("role", "admin")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
