package com.carsocialmedia.backend.forums.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Payload for editing a reply the caller authored. The text and the tagged people/cars are
 * editable; unlike a thread's body, a reply's content is mandatory, so blank is rejected.
 *
 * <p>The tag lists are PATCH-shaped: {@code null} leaves the current set untouched, a non-null list
 * <em>replaces</em> it wholesale (an empty list clears every tag). The "a car's owner must be
 * tagged too" rule is checked against the <em>resulting</em> tag set.
 *
 * @param content the new reply text (required, non-blank)
 * @param taggedPeople the complete new set of tagged profile ids, or {@code null} to leave unchanged
 * @param taggedCars the complete new set of tagged car ids, or {@code null} to leave unchanged
 */
public record UpdateReplyRequest(
        @NotBlank @Size(max = 20000) String content,
        @Size(max = 30) List<@NotNull UUID> taggedPeople,
        @Size(max = 30) List<@NotNull UUID> taggedCars
) {}
