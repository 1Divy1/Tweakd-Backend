package com.carsocialmedia.backend.storage.internal;

import com.carsocialmedia.backend.storage.internal.enums.FileFormat;
import com.carsocialmedia.backend.storage.internal.enums.ModificationPhase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Batch request for modification media presigned upload URLs.
 *
 * Each item describes one file Flutter intends to upload: its phase (before/after)
 * and format (webp image or mp4 video). The backend generates one presigned PUT URL
 * per item and returns them all in a single response.
 *
 * @param files the files to upload; between 1 and 10 items
 */
public record ModificationUploadRequest(
        @NotNull @Size(min = 1, max = 10) @Valid List<MediaItem> files
) {

    /**
     * @param phase  whether this is a before or after shot
     * @param format the file format (webp for images, mp4 for videos)
     */
    public record MediaItem(
            @NotNull ModificationPhase phase,
            @NotNull FileFormat format
    ) {}
}
