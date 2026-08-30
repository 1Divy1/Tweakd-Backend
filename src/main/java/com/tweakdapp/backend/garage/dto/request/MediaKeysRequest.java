package com.tweakdapp.backend.garage.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload carrying a list of R2 object keys.
 *
 * Used to set a car's gallery (the list is the complete desired state — the backend
 * replaces all gallery rows with it; an empty list clears the gallery) and to identify
 * gallery or modification media to delete (only keys that belong to the target are acted on).
 *
 * @param keys ordered list of bucket-relative R2 object keys (max 20)
 */
public record MediaKeysRequest(
        @NotNull @Size(max = 20) List<String> keys
) {}
