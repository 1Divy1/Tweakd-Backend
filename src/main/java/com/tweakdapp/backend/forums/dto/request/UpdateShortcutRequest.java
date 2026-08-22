package com.tweakdapp.backend.forums.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * Partial update for a shortcut: only non-null fields are applied ({@code null} means "leave
 * unchanged"). Reordering is a separate bulk operation; the filter itself (brand/model/topic) is
 * immutable — delete and recreate to change it.
 *
 * @param name the new label, or {@code null} to leave unchanged
 * @param notifyEnabled the new notify flag (wire name {@code notify}), or {@code null} to leave unchanged
 */
public record UpdateShortcutRequest(
        @Size(max = 100) String name,
        @JsonProperty("notify") Boolean notifyEnabled
) {}
