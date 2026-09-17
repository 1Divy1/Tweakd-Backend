package com.tweakdapp.backend.shared.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

/**
 * Wires the rate limiter and mounts it on the API routes.
 *
 * <p>Everything the interceptor needs is declared here as a bean rather than component-scanned.
 * That is what keeps controller slice tests working: {@code @WebMvcTest} picks up
 * {@link WebMvcConfigurer}s and interceptors but not arbitrary components, so a scanned registry or
 * properties bean would be missing and every slice test would fail to start a context (the problem
 * {@code BanEnforcementTestStub} exists to solve for the ban interceptor). Declared this way, all
 * 26 controller tests get the real limiter with its default {@link RateLimitProperties.Mode#LOG_ONLY}
 * mode, which counts but never blocks.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig implements WebMvcConfigurer {

    /**
     * After {@code BannedUserInterceptor} (which registers at the default order 0): a banned
     * account should be told it is banned, not told to slow down.
     */
    private static final int ORDER = 10;

    private final RateLimitInterceptor interceptor;

    RateLimitConfig(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.interceptor = new RateLimitInterceptor(
                properties,
                new RateLimiterRegistry(properties),
                new ClientIpResolver(properties.getClientIpStrategy()),
                objectMapper);
    }

    /** Exposed so tests can assert on the wired interceptor. */
    @Bean
    RateLimitInterceptor rateLimitInterceptor() {
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                // /api/storage/** is a sibling of /api/v1/** (presigned upload URLs), so match on
                // /api/** to cover both, plus the one unauthenticated route tree.
                .addPathPatterns("/api/**", "/public/**")
                .order(ORDER);
    }
}
