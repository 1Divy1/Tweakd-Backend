package com.carsocialmedia.backend.forums.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Payload for creating a thread. Everything else (id, author, counts, ranking score, timestamps,
 * derived brand) is server- or trigger-assigned.
 *
 * <p>Car scoping is one of three shapes:
 * <ul>
 *   <li>{@code modelId} set → a model-scoped thread; {@code brandId} is ignored (the DB trigger
 *       derives brand from the model).</li>
 *   <li>{@code brandId} set, {@code modelId} null → a brand-level thread.</li>
 *   <li>both null → a general thread.</li>
 * </ul>
 *
 * @param title the thread title (required, ≤200 chars)
 * @param content the optional OP body
 * @param modelId the car model this thread is about, or {@code null}
 * @param brandId the car brand this thread is about (used only when {@code modelId} is null), or {@code null}
 * @param topicIds the topic slugs to tag the thread with (optional, deduplicated server-side)
 */
public record CreateThreadRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 20000) String content,
        UUID modelId,
        UUID brandId,
        @Size(max = 10) List<@NotNull String> topicIds
) {}
