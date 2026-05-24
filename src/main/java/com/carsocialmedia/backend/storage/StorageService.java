package com.carsocialmedia.backend.storage;

import com.carsocialmedia.backend.storage.dto.UploadUrlResponse;
import com.carsocialmedia.backend.storage.internal.enums.FileFormat;
import com.carsocialmedia.backend.storage.internal.enums.ModificationPhase;

import java.util.UUID;

public interface StorageService {
    UploadUrlResponse coverUploadUrlRequest(UUID carId);
    UploadUrlResponse galleryUploadUrlRequest(UUID carId);
    UploadUrlResponse modificationUploadUrlRequest(UUID carId, UUID modId, ModificationPhase phase, FileFormat format);
}
