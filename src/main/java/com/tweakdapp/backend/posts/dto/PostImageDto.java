package com.tweakdapp.backend.posts.dto;

import java.util.UUID;

/**
 * A single image of a post, ready for rendering.
 *
 * The {@code imageUrl} is the full, client-usable URL built by the service from the
 * stored Cloudflare R2 object key — the raw key is never exposed to clients.
 *
 * @param id the image ID
 * @param imageUrl the full image URL (built from the R2 object key)
 * @param displayOrder the position of this image within the post (ascending)
 */
public record PostImageDto(
        UUID id,
        String imageUrl,
        short displayOrder
) {}