package com.tweakdapp.backend.garage.dto;

/**
 * A reference to a stored media object, returned on every read.
 *
 * The client displays {@code url} (a full public R2 URL, built by the backend) and sends
 * {@code key} back to key-based endpoints (gallery replace/delete, modification media delete).
 * This avoids the client having to reverse-engineer the key from the URL.
 *
 * @param key the bucket-relative R2 object key (what the client persists / sends back)
 * @param url the full public R2 URL (what the client displays); {@code null} if there is no object
 */
public record MediaRefDto(String key, String url) {}