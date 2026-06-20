package com.carsocialmedia.backend.profile.dto;

/**
 * A community role a user can identify with (e.g. enthusiast, mechanic, dealer).
 *
 * @param id   the role ID
 * @param name the display name
 */
public record CommunityRoleDto(String id, String name) {}