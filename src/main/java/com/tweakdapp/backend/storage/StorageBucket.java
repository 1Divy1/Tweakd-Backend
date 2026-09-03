package com.tweakdapp.backend.storage;

/**
 * Logical identifier for an R2 bucket target.
 *
 * Persisted media is referenced by (bucket, key). The actual bucket name and public URL are
 * resolved from configuration at runtime via this enum, so renaming a bucket or moving its
 * domain only touches config — the stored keys never change. Add a constant here (and a
 * matching {@code cloudflare.r2.*} config entry) when a new bucket is introduced.
 */
public enum StorageBucket {
    GARAGE,
    POSTS,
    AVATARS,
    BUSINESS,
    MAP_EVENTS,
    /**
     * Shared app assets that ship with the product rather than being uploaded by users: badge
     * artwork today, any other static SVG/image the app fetches by key tomorrow. Nothing presigns
     * into this bucket — its contents are put there out of band, and the backend only ever builds
     * public URLs for keys already recorded in the database.
     */
    ASSETS
}