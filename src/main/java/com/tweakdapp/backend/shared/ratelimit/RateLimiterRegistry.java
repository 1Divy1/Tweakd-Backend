package com.tweakdapp.backend.shared.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;

/**
 * The live token buckets, keyed by {@code <caller>|<limit name>}.
 *
 * <p>In-memory and per instance — no Redis. That is a deliberate cost decision (see
 * {@code RATE_LIMITING_PROGRESS.md}): with Cloud Run usually running one instance the counts are
 * effectively exact, and the worst case on three instances is a caller getting 3x a limit during a
 * spike, which still stops the abuse these limits exist for. Bucket4j's distributed backends take
 * the same {@link Bucket} API, so moving counters to Redis later would not touch endpoint code.
 *
 * <p>Caffeine, not a plain map: buckets must expire. A key is created on first sight, so
 * {@code /public/**} traffic from rotating IPs would otherwise grow the map without bound. Eviction
 * is by idleness and by total size, both from {@link RateLimitProperties}.
 */
public class RateLimiterRegistry {

    private final Cache<String, Bucket> buckets;

    RateLimiterRegistry(RateLimitProperties properties) {
        this.buckets = Caffeine.newBuilder()
                .maximumSize(properties.getMaxTrackedKeys())
                .expireAfterAccess(properties.getIdleEviction())
                .build();
    }

    /**
     * The caller's bucket for one limit, created on first use.
     *
     * <p>The bucket is built from {@code limit} only on a miss, so a request against an existing
     * bucket costs a cache lookup and an atomic token update — no allocation of bandwidth objects
     * per request.
     */
    Bucket bucket(String key, RateLimitProperties.Limit limit) {
        return buckets.get(key, ignored -> Bucket.builder()
                .addLimit(limit.toBandwidth())
                .build());
    }

    /** How many buckets are currently tracked. For logging and tests, not for decisions. */
    long trackedKeys() {
        buckets.cleanUp();
        return buckets.estimatedSize();
    }
}
