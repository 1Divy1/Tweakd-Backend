package com.tweakdapp.backend.shared.ratelimit;

import com.tweakdapp.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the annotations and the configuration in sync across the whole application.
 *
 * <p>A {@link RateLimited} annotation naming a limit that is not configured would fail open — the
 * endpoint would look protected while being limited by nothing but the general bucket. The
 * interceptor logs that loudly at runtime; this test is what stops it from ever shipping.
 */
@SpringBootTest
class RateLimitConfigIT extends AbstractPostgresIT {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private RateLimitProperties properties;

    @Test
    void everyAnnotatedEndpointNamesAConfiguredLimit() {
        Set<String> annotated = handlerMapping.getHandlerMethods().values().stream()
                .map(RateLimitConfigIT::limitNameOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        assertThat(annotated)
                .as("no endpoint carries @RateLimited — the annotations were lost")
                .isNotEmpty();
        assertThat(properties.getLimits().keySet())
                .as("@RateLimited names with no configured limit")
                .containsAll(annotated);
    }

    @Test
    void everyDeclaredLimitNameHasADefault() throws Exception {
        // GENERAL and PUBLIC_BACKSTOP are their own properties, not entries in the map.
        List<String> mapped = declaredLimitNames().stream()
                .filter(name -> !name.equals(RateLimits.GENERAL) && !name.equals(RateLimits.PUBLIC_BACKSTOP))
                .toList();

        assertThat(properties.getLimits().keySet())
                .as("RateLimits constants missing a default in RateLimitProperties")
                .containsAll(mapped);
    }

    @Test
    void everyConfiguredLimitIsUsable() {
        properties.getLimits().forEach((name, limit) -> {
            assertThat(limit.getCapacity()).as("%s capacity", name).isPositive();
            assertThat(limit.getRefillTokens()).as("%s refill tokens", name).isPositive();
            assertThat(limit.getRefillPeriod()).as("%s refill period", name).isNotNull();
            assertThat(limit.getRefillPeriod().isPositive()).as("%s refill period", name).isTrue();
            // Proves the values translate into a real Bucket4j bandwidth (e.g. refill not > capacity).
            assertThat(limit.toBandwidth()).isNotNull();
        });
    }

    private static String limitNameOf(HandlerMethod handlerMethod) {
        RateLimited annotation = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (annotation == null) {
            annotation = handlerMethod.getBeanType().getAnnotation(RateLimited.class);
        }
        return annotation == null ? null : annotation.value();
    }

    private static List<String> declaredLimitNames() throws Exception {
        List<String> names = new java.util.ArrayList<>();
        for (Field field : RateLimits.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                names.add((String) field.get(null));
            }
        }
        return names;
    }
}
