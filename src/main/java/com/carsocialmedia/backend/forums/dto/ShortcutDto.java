package com.carsocialmedia.backend.forums.dto;

import com.carsocialmedia.backend.garage.dto.CarBrandDto;
import com.carsocialmedia.backend.garage.dto.CarModelDto;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's saved forum filter, resolved for display. At least one of {@code brand} / {@code model} /
 * {@code topic} is non-null.
 *
 * @param id the shortcut id
 * @param name the user-chosen label
 * @param brand the brand filter, or {@code null}
 * @param model the model filter, or {@code null}
 * @param topic the topic filter, or {@code null}
 * @param sortOrder the pinned position in the user's list
 * @param notifyEnabled whether the user opted into notifications for this filter (wire name {@code notify})
 * @param createdAt when the shortcut was created
 */
public record ShortcutDto(
        UUID id,
        String name,
        CarBrandDto brand,
        CarModelDto model,
        TopicDto topic,
        int sortOrder,
        @JsonProperty("notify") boolean notifyEnabled,
        Instant createdAt
) {}
