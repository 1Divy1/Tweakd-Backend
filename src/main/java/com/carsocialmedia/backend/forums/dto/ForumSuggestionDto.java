package com.carsocialmedia.backend.forums.dto;

/**
 * One "popular hub" suggestion for the forums empty state / discovery, mixing car categories and
 * topics into a single activity-ranked list (e.g. "BMW M4", "Supra MK5", "Engine"). The client
 * navigates using {@code type} + {@code id} (a brand/model UUID string, or a topic slug).
 *
 * @param type {@code brand} | {@code model} | {@code topic}
 * @param id the brand/model UUID (as a string) or the topic slug — pair with {@code type} to route
 * @param name the primary label (brand name, model name, or topic name)
 * @param subtitle secondary label — the brand name for a model (so "M4" reads as "BMW M4"); {@code null} otherwise
 * @param threadCount how many threads this hub has (the ranking key)
 */
public record ForumSuggestionDto(
        String type,
        String id,
        String name,
        String subtitle,
        int threadCount
) {}
