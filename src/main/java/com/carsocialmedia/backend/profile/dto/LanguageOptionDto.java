package com.carsocialmedia.backend.profile.dto;

/**
 * A selectable UI language for the app.
 *
 * @param id       the language code (e.g. {@code "en"}, {@code "ro"}) — what {@code profiles.app_language} stores
 * @param language the display name (e.g. {@code "English"}, {@code "Romanian"})
 */
public record LanguageOptionDto(String id, String language) {}
