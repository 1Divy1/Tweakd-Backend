package com.carsocialmedia.backend.profile.internal;

import com.carsocialmedia.backend.shared.exception.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.UUID;

/**
 * Rejects every request from a banned user with 403. Runs as a MVC interceptor (after the security
 * filter chain, so the JWT principal is available) and reads ban state through {@link BanCache}, so
 * the added cost is one cached lookup per request. JWTs stay valid until they expire — this is what
 * actually enforces a ban.
 */
@Component
class BannedUserInterceptor implements HandlerInterceptor {

    private final BanCache banCache;
    private final ObjectMapper objectMapper;

    BannedUserInterceptor(BanCache banCache, ObjectMapper objectMapper) {
        this.banCache = banCache;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return true;
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            return true; // not a user token; nothing to enforce
        }

        if (!banCache.isBanned(userId)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.of(HttpServletResponse.SC_FORBIDDEN, "Your account has been banned")));
        return false;
    }

    /** Registers the interceptor for every route; the cheap cache lookup makes that affordable. */
    @Configuration
    static class Registration implements WebMvcConfigurer {

        private final BannedUserInterceptor interceptor;

        Registration(BannedUserInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(interceptor);
        }
    }
}
