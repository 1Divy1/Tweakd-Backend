package com.tweakdapp.backend.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Mounts {@link OriginSecretFilter} in front of every other servlet filter, Spring Security's
 * included.
 *
 * <p>Registered through a {@link FilterRegistrationBean} in a plain configuration class instead of
 * a {@code @Component} filter on purpose: {@code @WebMvcTest} slices pick up filter components but
 * not this class, so controller tests never see the guard.
 */
@Configuration
class OriginGuardConfig {

    private static final Logger log = LoggerFactory.getLogger(OriginGuardConfig.class);

    @Bean
    FilterRegistrationBean<OriginSecretFilter> originSecretFilter(@Value("${origin-guard.secret:}") String secret) {
        OriginSecretFilter filter = new OriginSecretFilter(secret);
        if (filter.isEnabled()) {
            log.info("Origin guard enabled: requests without a valid {} header are rejected", OriginSecretFilter.HEADER);
        } else {
            log.warn("Origin guard DISABLED (ORIGIN_SECRET not set): the *.run.app URL accepts requests "
                    + "that bypassed Cloudflare. Expected only locally and in tests.");
        }

        FilterRegistrationBean<OriginSecretFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
