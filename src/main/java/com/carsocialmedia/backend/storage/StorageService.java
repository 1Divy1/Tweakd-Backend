package com.carsocialmedia.backend.storage;

import com.carsocialmedia.backend.storage.dto.ModificationUploadUrlsResponse;
import com.carsocialmedia.backend.storage.dto.UploadUrlResponse;
import com.carsocialmedia.backend.storage.internal.ModificationUploadRequest;
import com.carsocialmedia.backend.storage.internal.enums.FileFormat;
import com.carsocialmedia.backend.storage.internal.enums.ModificationPhase;

import java.util.List;
import java.util.UUID;

public interface StorageService {
    UploadUrlResponse coverUploadUrlRequest(UUID carId);
    UploadUrlResponse galleryUploadUrlRequest(UUID carId);
    UploadUrlResponse modificationUploadUrlRequest(UUID carId, UUID modId, ModificationPhase phase, FileFormat format);
    ModificationUploadUrlsResponse modificationBatchUploadUrlRequest(UUID carId, UUID modId, List<ModificationUploadRequest.MediaItem> files);

    /**
     * Deletes objects from R2 by their public URLs. URLs that don't match any known
     * bucket's public base URL are skipped with a warning.
     */
    void deleteObjects(List<String> urls);
}
