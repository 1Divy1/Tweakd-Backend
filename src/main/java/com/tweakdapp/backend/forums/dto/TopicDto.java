package com.tweakdapp.backend.forums.dto;

/**
 * A forum topic for the picker and for a thread's topic chips.
 *
 * @param id the topic slug (e.g. {@code "tuning"})
 * @param name the display name
 * @param sortOrder the curated position in the topic list
 * @param color optional accent color (hex), or {@code null}
 * @param threadCount number of threads tagged with this topic
 */
public record TopicDto(
        String id,
        String name,
        int sortOrder,
        String color,
        int threadCount
) {}
