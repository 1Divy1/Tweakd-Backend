package com.tweakdapp.backend.forums.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Payload for reordering a user's shortcuts: the complete list of the user's shortcut ids in the
 * desired order. Each id's {@code sortOrder} is set to its position in the list.
 *
 * @param orderedIds the user's shortcut ids in the new order (must be the user's full set)
 */
public record ReorderShortcutsRequest(
        @NotEmpty List<@NotNull UUID> orderedIds
) {}
