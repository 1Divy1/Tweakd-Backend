package com.tweakdapp.backend.garage.dto;

import java.util.List;

/**
 * Who built the car, as shown to an anonymous visitor. The profile's UUID is deliberately absent:
 * the page links to a person by username, and an internal id on a public page is a handle for
 * scraping and nothing else.
 *
 * @param username        the owner's @handle
 * @param name            their display name (may be null)
 * @param avatarUrl       full public avatar URL (may be null)
 * @param isVerified      whether the account is verified
 * @param reputationScore their reputation score — the "this person knows what they're doing" signal
 * @param badges          the badges they have earned, newest first. Empty, never null
 */
public record PublicCarOwnerDto(
        String username,
        String name,
        String avatarUrl,
        boolean isVerified,
        int reputationScore,
        List<PublicBadgeDto> badges
) {}
