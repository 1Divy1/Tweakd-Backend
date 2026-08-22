package com.tweakdapp.backend.garage.dto;

import java.util.UUID;

/**
 * Compact identity of the profile that owns a car. Lets clients attribute a car
 * to a user (and link to their profile) without a separate lookup.
 *
 * @param id the owner's profile ID
 * @param username the owner's username
 */
public record CarOwnerDto(UUID id, String username) {}