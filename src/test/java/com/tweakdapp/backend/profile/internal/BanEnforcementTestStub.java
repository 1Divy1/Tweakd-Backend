package com.tweakdapp.backend.profile.internal;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Slice-test stand-in for the ban-enforcement wiring.
 *
 * <p>{@code @WebMvcTest} slices pick up {@link BannedUserInterceptor} and its
 * {@code Registration} (interceptors and {@code WebMvcConfigurer}s are part of the web slice)
 * but not {@link BanCache}, which is a plain component with a repository dependency. This stub
 * supplies a Mockito {@code BanCache} whose {@code isBanned} defaults to {@code false}, so web
 * tests run with the real interceptor in place; a test can stub the bean to simulate a banned
 * user. Imported automatically by {@code @AppWebMvcTest} — lives in this package because
 * {@code BanCache} is package-private.
 */
@TestConfiguration
public class BanEnforcementTestStub {

    @Bean
    BanCache banCache() {
        return Mockito.mock(BanCache.class);
    }
}
