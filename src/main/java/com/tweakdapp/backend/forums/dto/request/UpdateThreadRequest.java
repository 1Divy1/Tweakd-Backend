package com.tweakdapp.backend.forums.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Payload for editing a thread the caller authored. Reddit-style: the title, car scoping, and
 * topics are fixed at creation — only the OP body and the tagged people/cars are editable. Sending
 * a blank string clears the body (it is optional on a thread).
 *
 * <p>The tag lists are PATCH-shaped: {@code null} leaves the current set untouched, a non-null list
 * <em>replaces</em> it wholesale (an empty list clears every tag). The "a car's owner must be
 * tagged too" rule is checked against the <em>resulting</em> tag set, so untagging a person whose
 * car stays tagged is rejected.
 *
 * @param content the new OP body (required field; blank clears it)
 * @param taggedPeople the complete new set of tagged profile ids, or {@code null} to leave unchanged
 * @param taggedCars the complete new set of tagged car ids, or {@code null} to leave unchanged
 */
public record UpdateThreadRequest(
        @NotNull @Size(max = 20000) String content,
        @Size(max = 30) List<@NotNull UUID> taggedPeople,
        @Size(max = 30) List<@NotNull UUID> taggedCars
) {}
