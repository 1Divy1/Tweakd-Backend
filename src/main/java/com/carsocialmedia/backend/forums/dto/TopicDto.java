package com.carsocialmedia.backend.forums.dto;

/**
 * A forum topic for the picker and for a thread's topic chips.
 *
 * @param id the topic slug (e.g. {@code "tuning"})
 * @param name the display name
 * @param kind {@code component} or {@code format} — how topics are grouped
 * @param sortOrder the curated position within its kind
 * @param color optional accent color (hex), or {@code null}
 */
public record TopicDto(
        String id,
        String name,
        String kind,
        int sortOrder,
        String color
) {}
