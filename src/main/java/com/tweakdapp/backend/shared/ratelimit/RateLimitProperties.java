package com.tweakdapp.backend.shared.ratelimit;

import io.github.bucket4j.Bandwidth;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rate limiting configuration, prefix {@code rate-limit}.
 *
 * <p><strong>Defaults live here, not in {@code application.yaml}.</strong> Every limit has a
 * working default in this class, so the limiter behaves identically in tests, locally and in
 * production unless a value is deliberately overridden. {@code application.yaml} therefore only
 * needs to set {@code mode} plus whatever is being tuned:
 *
 * <pre>{@code
 * rate-limit:
 *   mode: ENFORCE
 *   limits:
 *     comments:
 *       capacity: 20
 * }</pre>
 *
 * <p>Counters are per instance and in memory (see {@link RateLimiterRegistry}). With Cloud Run
 * capped at 3 instances a caller can get up to 3x a limit during a spike, and a deploy resets all
 * counters. That is accepted: these limits exist to stop abuse, not to meter exact quotas. A
 * business rule that must be exact (&quot;3 map events per day&quot;) belongs in the owning
 * service as a row count inside its transaction, not here.
 */
@ConfigurationProperties(prefix = "rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    /** What the limiter does when a caller is over a limit. */
    public enum Mode {
        /** Limiter disabled entirely — no counting, no logging. */
        OFF,
        /**
         * Count and log what <em>would</em> have been blocked, but let every request through.
         * The safe starting mode: it produces the data needed to tune the numbers against real
         * traffic (opening the app fires a burst of requests) before anyone gets a 429.
         */
        LOG_ONLY,
        /** Reject over-limit requests with 429 and {@code Retry-After}. */
        ENFORCE
    }

    /**
     * How to identify an unauthenticated caller — which entry of {@code X-Forwarded-For} to trust.
     * See {@link ClientIpResolver} for why this is configurable.
     */
    public enum ClientIpStrategy {
        /** The last entry, i.e. the one the closest proxy appended. Default. */
        XFF_LAST,
        /** The first entry. Only correct when a trusted proxy rewrites the header. */
        XFF_FIRST,
        /** Ignore the header and use the socket address. */
        REMOTE_ADDR
    }

    private Mode mode = Mode.LOG_ONLY;

    private ClientIpStrategy clientIpStrategy = ClientIpStrategy.XFF_LAST;

    /**
     * Upper bound on tracked buckets. Reaching it evicts the least recently used bucket, which
     * effectively forgives that caller — a bound is still required, because an attacker rotating
     * IPs against {@code /public/**} would otherwise grow the map until the instance runs out of
     * memory.
     */
    private int maxTrackedKeys = 50_000;

    /** A bucket unused for this long is dropped; the caller starts fresh (full bucket) after it. */
    private Duration idleEviction = Duration.ofMinutes(15);

    /**
     * The broad limit every authenticated request consumes from.
     *
     * <p>240/min (4/s sustained) is deliberate headroom: app launch is only a handful of calls
     * (feed page, unread count, profile, device registration) and the busiest plausible human
     * minute — scrolling the feed while opening profiles, garages and events — is well under 100.
     * A limit this blunt must never be what stops a real user, so the per-action limits below are
     * the ones doing the actual work; this one exists to stop a script hammering the API.
     */
    private Limit general = Limit.of(240, 240, Duration.ofMinutes(1));

    /**
     * Per-IP limit for unauthenticated {@code /public/**} requests.
     *
     * <p>Deliberately generous: real visitors to a shared car page never reach the backend
     * directly — the web.tweakdapp.com Worker fetches it for them and caches the response — so
     * this is a backstop against someone calling the {@code run.app} URL, and the IPs it sees are
     * usually Cloudflare's. Per-visitor limiting for that page belongs on Cloudflare.
     */
    private Limit publicBackstop = Limit.of(600, 600, Duration.ofMinutes(1));

    /**
     * Named limits referenced by {@link RateLimited}. Seeded with a working default for every
     * {@link RateLimits} constant; entries in {@code application.yaml} override by name.
     */
    private Map<String, Limit> limits = defaultLimits();

    /** Resolves a named limit, or {@code null} when the name is not configured. */
    Limit limit(String name) {
        return limits.get(name);
    }

    /**
     * The limits, set to enforce from day one rather than tuned from {@link Mode#LOG_ONLY} data.
     *
     * <p>Read "capacity 10, then 1 per 10s" as: ten in a row is fine, after which one more every
     * ten seconds. Capacity covers the legitimate burst; the refill rate is what a script is held
     * down to.
     *
     * <p>Every number below is set from how long the action physically takes a person, then given
     * room on top — the cost of a limit being too generous is that abuse is merely slowed, while
     * the cost of one being too tight is a real user blocked from using the app. Writing a comment
     * takes longer than six seconds; photographing and posting a car takes minutes. The refill
     * rates are what matter against automation: 10 comments a minute and 30 posts an hour make
     * spamming pointless, while no human notices the ceiling.
     */
    private static Map<String, Limit> defaultLimits() {
        Map<String, Limit> defaults = new LinkedHashMap<>();
        // Composing a post or a thread is minutes of work; five back to back is already unusual.
        defaults.put(RateLimits.POSTS_CREATE, Limit.of(5, 1, Duration.ofMinutes(2)));
        defaults.put(RateLimits.FORUMS_THREAD, Limit.of(5, 1, Duration.ofMinutes(2)));
        // A lively thread: 15 replies in a row, then one every 6s. Typing takes longer than that.
        defaults.put(RateLimits.COMMENTS, Limit.of(15, 1, Duration.ofSeconds(6)));
        // Liking while scrolling fast is the most rapid-fire thing a real user does, so this is
        // the loosest of the action limits: a like a second, sustained.
        defaults.put(RateLimits.REACTIONS, Limit.of(60, 1, Duration.ofSeconds(1)));
        // Room for someone reporting a whole spam wave in one sitting.
        defaults.put(RateLimits.REPORTS, Limit.of(10, 1, Duration.ofMinutes(2)));
        defaults.put(RateLimits.FEEDBACK_CREATE, Limit.of(5, 1, Duration.ofMinutes(5)));
        // An organizer setting up several meets in one sitting, rather than one per hour.
        defaults.put(RateLimits.MAPEVENTS_CREATE, Limit.of(5, 1, Duration.ofMinutes(20)));
        defaults.put(RateLimits.MAPEVENTS_PARTICIPATE, Limit.of(20, 1, Duration.ofSeconds(15)));
        // Building out a car is a legitimately busy session: the car, then a dozen modifications.
        defaults.put(RateLimits.GARAGE_WRITE, Limit.of(30, 1, Duration.ofSeconds(5)));
        // One call mints up to 10 URLs (see PostImagesUploadRequest / ModificationUploadRequest),
        // so this is per batch, not per image — 40 covers a long editing session.
        defaults.put(RateLimits.UPLOADS, Limit.of(40, 1, Duration.ofSeconds(5)));
        // Push-token registration runs on every app start and sign-in, plus FCM token refreshes —
        // not on anything the user asked for. Someone opening the app ten times in an hour is
        // normal, and a failure here silently costs that device its push notifications, so this is
        // generous on purpose. Abuse potential is nil: it is an idempotent upsert keyed by token.
        defaults.put(RateLimits.DEVICES, Limit.of(30, 1, Duration.ofMinutes(1)));
        return defaults;
    }

    /** One token bucket's shape: burst size plus sustained refill rate. */
    @Getter
    @Setter
    public static class Limit {

        /** Tokens the bucket holds — the burst a caller may spend at once. */
        private long capacity;

        /** Tokens added every {@link #refillPeriod}. */
        private long refillTokens;

        /** How often {@link #refillTokens} are added. */
        private Duration refillPeriod;

        public static Limit of(long capacity, long refillTokens, Duration refillPeriod) {
            Limit limit = new Limit();
            limit.capacity = capacity;
            limit.refillTokens = refillTokens;
            limit.refillPeriod = refillPeriod;
            return limit;
        }

        /**
         * The Bucket4j bandwidth for this limit. Greedy refill adds tokens smoothly across the
         * period rather than all at once on a tick boundary, which avoids a stampede the moment a
         * window rolls over.
         */
        Bandwidth toBandwidth() {
            return Bandwidth.builder()
                    .capacity(capacity)
                    .refillGreedy(refillTokens, refillPeriod)
                    .build();
        }
    }
}
