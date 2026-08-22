package com.tweakdapp.backend.forums.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Payload for creating a shortcut (a saved filter). At least one of {@code brandId} / {@code modelId}
 * / {@code topicId} must be non-null (enforced server-side and by a DB CHECK). The new shortcut is
 * appended at the end of the user's list.
 *
 * @param name the user-chosen label (required)
 * @param brandId the brand to filter by, or {@code null}
 * @param modelId the model to filter by, or {@code null}
 * @param topicId the topic slug to filter by, or {@code null}
 * @param notifyEnabled whether to enable notifications for this filter (wire name {@code notify};
 *                     defaults to false when omitted)
 */
public record CreateShortcutRequest(
        @NotBlank @Size(max = 100) String name,
        UUID brandId,
        UUID modelId,
        String topicId,
        @JsonProperty("notify") Boolean notifyEnabled
) {}
