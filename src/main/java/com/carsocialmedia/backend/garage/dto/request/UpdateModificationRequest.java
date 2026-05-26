package com.carsocialmedia.backend.garage.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Partial-update payload for a car modification.
 *
 * Every field is nullable — null means "leave the existing value unchanged". Only the
 * fields you want to change need to be provided. This applies to both text fields and
 * media: {@code addMedia} appends new before/after items, {@code removeMediaUrls} deletes
 * existing ones by URL.
 *
 * @param categoryId modification category ID; null = no change
 * @param title short title (max 100 chars); null = no change
 * @param description detailed description (max 1000 chars); null = no change
 * @param installationDate when the mod was installed; null = no change
 * @param price cost of the modification; null = no change
 * @param isPricePublic whether the price is visible to other users; null = no change
 * @param mileageAtInstall mileage reading at installation; null = no change
 * @param addMedia media items to append (each requires a URL and a phase)
 * @param removeMediaUrls URLs of existing media items to delete
 */
public record UpdateModificationRequest(
        String categoryId,

        @Size(max = 100) String title,

        @Size(max = 1000) String description,

        Instant installationDate,

        @Positive Float price,

        Boolean isPricePublic,

        @Positive Integer mileageAtInstall,

        @Valid List<MediaItem> addMedia,

        List<String> removeMediaUrls
) {

    /**
     * A single media item to add to a modification.
     *
     * @param url the public R2 URL of the uploaded file
     * @param phase either {@code "before"} or {@code "after"}
     */
    public record MediaItem(
            @NotBlank String url,
            @NotBlank @Pattern(regexp = "before|after", message = "phase must be 'before' or 'after'") String phase
    ) {}
}