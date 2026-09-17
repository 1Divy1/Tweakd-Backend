package com.tweakdapp.backend.shared.ratelimit;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Bucket identity, token accounting and the eviction bound that keeps the map from growing. */
class RateLimiterRegistryTest {

    private static final RateLimitProperties.Limit TWO_PER_MINUTE =
            RateLimitProperties.Limit.of(2, 2, Duration.ofMinutes(1));

    private static RateLimiterRegistry registry(int maxTrackedKeys) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setMaxTrackedKeys(maxTrackedKeys);
        properties.setIdleEviction(Duration.ofMinutes(15));
        return new RateLimiterRegistry(properties);
    }

    @Test
    void sameKeyReturnsTheSameBucketSoTokensAccumulate() {
        RateLimiterRegistry registry = registry(100);

        Bucket first = registry.bucket("user:a|general", TWO_PER_MINUTE);
        assertThat(first.tryConsume(1)).isTrue();
        assertThat(registry.bucket("user:a|general", TWO_PER_MINUTE).tryConsume(1)).isTrue();

        // Capacity 2 is now spent, and the refill is a minute away.
        assertThat(registry.bucket("user:a|general", TWO_PER_MINUTE).tryConsume(1)).isFalse();
    }

    @Test
    void differentCallersAndDifferentLimitsGetSeparateBuckets() {
        RateLimiterRegistry registry = registry(100);

        assertThat(registry.bucket("user:a|comments", TWO_PER_MINUTE).tryConsume(2)).isTrue();

        // Another user is untouched, and so is the same user's other limit.
        assertThat(registry.bucket("user:b|comments", TWO_PER_MINUTE).tryConsume(2)).isTrue();
        assertThat(registry.bucket("user:a|reactions", TWO_PER_MINUTE).tryConsume(2)).isTrue();
    }

    @Test
    void trackedBucketsStayUnderTheConfiguredBound() {
        RateLimiterRegistry registry = registry(10);

        for (int i = 0; i < 500; i++) {
            registry.bucket("ip:198.51.100." + i + "|public.backstop", TWO_PER_MINUTE).tryConsume(1);
        }

        // Without a bound, an attacker rotating IPs would grow this map until the instance died.
        assertThat(registry.trackedKeys()).isLessThanOrEqualTo(10);
    }
}
