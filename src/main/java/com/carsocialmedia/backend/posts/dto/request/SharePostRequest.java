package com.carsocialmedia.backend.posts.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Payload for sharing a post. The optional {@code content} is the sharer's own caption on the
 * re-share: a non-blank value makes it a "quote share" (counted separately in
 * {@code quote_shares_count}); a null/blank value is a plain share.
 *
 * @param content the sharer's caption, or null/blank for a plain share
 */
public record SharePostRequest(
        @Size(max = 2200) String content
) {}