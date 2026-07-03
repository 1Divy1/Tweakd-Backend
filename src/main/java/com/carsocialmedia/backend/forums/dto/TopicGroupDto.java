package com.carsocialmedia.backend.forums.dto;

import java.util.List;

/**
 * A group of topics that share a {@code kind} (e.g. all {@code component} topics), for the grouped
 * topic picker.
 *
 * @param kind the shared kind ({@code component} | {@code format})
 * @param topics the topics in this group, in curated order
 */
public record TopicGroupDto(
        String kind,
        List<TopicDto> topics
) {}
