package com.tweakdapp.backend.garage.dto;

/**
 * A media item on the public car page.
 *
 * <p>Note what is missing: the R2 object key. {@code MediaRefDto} carries one because in-app
 * clients send it back to key-based endpoints; an anonymous visitor has nothing to send it back to,
 * and the key is an internal storage detail.
 *
 * @param url   the public R2 URL
 * @param type  "image" or "video"
 * @param phase "before" or "after"
 */
public record PublicMediaDto(String url, String type, String phase) {}
