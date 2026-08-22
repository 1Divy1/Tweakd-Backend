package com.tweakdapp.backend.posts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload carrying a post's image keys in display order.
 *
 * The list is the complete desired state: the backend replaces all {@code post_images} rows with
 * it (position = list index) and deletes from R2 any previously stored key no longer present. An
 * empty list clears the post's images. Keys are the R2 object keys issued by the storage module's
 * upload-URL endpoint.
 *
 * @param keys ordered list of bucket-relative R2 object keys (max 10)
 */
public record PostImageKeysRequest(
        @NotNull @Size(max = 10) List<@NotBlank String> keys
) {}