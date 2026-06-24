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
     * Builds the public URL for a stored R2 object key. The bucket name and public domain are
     * resolved from configuration at call time, so persisted keys stay valid even if those change.
     *
     * @param bucket the logical bucket the key belongs to
     * @param key the bucket-relative R2 object key (as persisted by the owning module)
     * @return the full public URL, or {@code null} if {@code key} is null or blank
     */
    String publicUrl(StorageBucket bucket, String key);

    /**
     * Deletes objects from R2 by their bucket-relative keys. Null/blank keys are skipped.
     *
     * @param bucket the logical bucket the keys belong to
     * @param keys the R2 object keys to delete (empty list is a no-op)
     */
    void deleteByKeys(StorageBucket bucket, List<String> keys);
}
