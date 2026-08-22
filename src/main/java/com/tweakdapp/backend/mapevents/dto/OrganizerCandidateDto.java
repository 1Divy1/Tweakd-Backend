package com.tweakdapp.backend.mapevents.dto;

import java.util.UUID;

/**
 * One hit from an organizer search — an app user or a business account the caller might add as a
 * co-organizer via {@code AddOrganizerRequest}. {@link #type} says which; the values match
 * {@link MapEventOrganizerDto#INDIVIDUAL} / {@link MapEventOrganizerDto#BUSINESS}.
 *
 * @param type        {@code individual} or {@code business}
 * @param referenceId the profile id or business id — what to send back as {@code userId}/{@code businessId}
 * @param name        display name (individual) or business name; never a username
 * @param username    the individual's {@code @username}; {@code null} for a business hit
 * @param imageUrl    avatar URL (individual) or logo URL (business); {@code null} if none
 */
public record OrganizerCandidateDto(
        String type,
        UUID referenceId,
        String name,
        String username,
        String imageUrl
) {}
