package com.tweakdapp.backend.forums.dto;

/**
 * One "popular hub" suggestion for the forums empty state / discovery, consisting of car brands or car brands + model.
 * (eg: "BMW" or "BMW M4").
 *
 * @param type {@code brand} | {@code model}
 * @param id the brand/model UUID (as a string)
 * @param name the primary label (brand name or model name)
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
