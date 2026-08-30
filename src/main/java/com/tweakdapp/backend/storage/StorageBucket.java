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
    MAP_EVENTS
}