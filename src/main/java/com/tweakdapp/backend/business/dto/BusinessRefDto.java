package com.tweakdapp.backend.business.dto;

import java.util.UUID;

/**
 * The smallest useful projection of a business — enough to render a credit or a chip that links to
 * the business profile, without paying for the full {@link BusinessDto} (opening hours and all).
 *
 * <p>The business counterpart of {@code ProfileSearchResultDto}, and used the same way: batch-resolve
 * ids, render name and image.
 *
 * @param id      the business id
 * @param name    display name
 * @param logoUrl logo image URL, or {@code null} if the business has none
 */
public record BusinessRefDto(
        UUID id,
        String name,
        String logoUrl
) {}
