package com.tweakdapp.backend.garage.dto;

/**
 * A badge on the public owner card: the artwork and its name, nothing else. No badge id (an
 * internal code), and no locked variant — what a stranger has left to unlock is not a visitor's
 * business.
 *
 * @param name     the badge's display title
 * @param imageUrl the full public URL of the earned artwork
 */
public record PublicBadgeDto(String name, String imageUrl) {}
