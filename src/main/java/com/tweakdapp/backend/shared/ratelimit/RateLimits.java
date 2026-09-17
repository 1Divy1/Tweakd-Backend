package com.tweakdapp.backend.shared.ratelimit;

/**
 * The names of the configured rate limits.
 *
 * <p>Constants rather than string literals so a typo in a {@link RateLimited} annotation is a
 * compile error instead of a limit that silently never applies. Every name here must have an entry
 * in {@link RateLimitProperties}' defaults — {@code RateLimitConfigIT} asserts exactly that.
 *
 * <p>Endpoints are grouped by what abuse of them costs, not by module: everything that creates
 * feed-visible content shares a bucket per kind, everything that merely reacts shares another.
 */
public final class RateLimits {

    /** Every authenticated request. Broad backstop against scripted hammering. */
    public static final String GENERAL = "general";

    /** Unauthenticated {@code /public/**} traffic, keyed by IP. See {@link ClientIpResolver}. */
    public static final String PUBLIC_BACKSTOP = "public.backstop";

    /** New feed posts (including participant cards). */
    public static final String POSTS_CREATE = "posts.create";

    /** New forum threads. */
    public static final String FORUMS_THREAD = "forums.thread";

    /** Comments and replies: post comments, forum replies, feedback comments. */
    public static final String COMMENTS = "comments";

    /**
     * Likes, saves, shares, votes, follows. Cheap individually, but each one can notify another
     * user — the abuse being limited is notification flooding, not database load.
     */
    public static final String REACTIONS = "reactions";

    /** Abuse reports. Limited because they create moderation work for staff. */
    public static final String REPORTS = "reports";

    /** Community feedback, feedback-feed messages, support tickets. */
    public static final String FEEDBACK_CREATE = "feedback.create";

    /** Creating map events and contests — each is a publishing action with staff review behind it. */
    public static final String MAPEVENTS_CREATE = "mapevents.create";

    /** Taking part in an event: car registration, contest entries, contest votes. */
    public static final String MAPEVENTS_PARTICIPATE = "mapevents.participate";

    /**
     * Garage writes: creating or editing cars, modifications and dream cars, and minting a car
     * share link. Each one is a row plus, usually, images behind it.
     */
    public static final String GARAGE_WRITE = "garage.write";

    /** Presigned upload URL endpoints. Abuse here costs R2 storage and bandwidth. */
    public static final String UPLOADS = "uploads";

    /** FCM device registration. */
    public static final String DEVICES = "devices";

    private RateLimits() {
    }
}
