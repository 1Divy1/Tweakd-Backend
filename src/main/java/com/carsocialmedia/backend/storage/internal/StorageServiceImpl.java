package com.carsocialmedia.backend.storage.internal;

import com.carsocialmedia.backend.storage.StorageService;
import com.carsocialmedia.backend.storage.dto.UploadUrlResponse;
import com.carsocialmedia.backend.storage.internal.cloudflare.PresignedUrlGenerator;
import com.carsocialmedia.backend.storage.internal.cloudflare.R2Config;
import com.carsocialmedia.backend.storage.internal.enums.FileFormat;
import com.carsocialmedia.backend.storage.internal.enums.ModificationPhase;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StorageServiceImpl implements StorageService {

    private final PresignedUrlGenerator presigner;
    private final R2Config config;

    StorageServiceImpl (PresignedUrlGenerator presigner, R2Config config) {
        this.presigner = presigner;
        this.config = config;
    }

    // === GARAGE ===
    // cars/{carId}/cover.webp
    @Override
    public UploadUrlResponse coverUploadUrlRequest(UUID carId) {
        String key = "cars/" + carId +
                "/cover" + FileFormat.WEBP.getExtension();

        return buildUploadUrlResponse(config.getGarage(), key, FileFormat.WEBP);
    }

    // TODO: consider modifying the file extension to allow videos too
    // cars/{carId}/gallery/{uuid}.webp
    @Override
    public UploadUrlResponse galleryUploadUrlRequest(UUID carId) {
        String key = "cars/" + carId +
                "/gallery/" + UUID.randomUUID() + FileFormat.WEBP.getExtension();

        return buildUploadUrlResponse(config.getGarage(), key, FileFormat.WEBP);
    }

    // cars/{carId}/modifications/{modId}/{phase}/{uuid}.{ext}
    @Override
    public UploadUrlResponse modificationUploadUrlRequest(UUID carId, UUID modId, ModificationPhase phase, FileFormat format) {
        String key = "cars/" + carId +
                "/modifications/" + modId +
                "/" + phase.folder() +
                "/" + UUID.randomUUID() + format.getExtension();

        return buildUploadUrlResponse(config.getGarage(), key, format);
    }

    // ========== HELPERS ==========

    private UploadUrlResponse buildUploadUrlResponse(R2Config.BucketTarget target, String key, FileFormat format) {

        // Generate the presigned URL for uploading
        String uploadUrl = presigner.generateUploadUrl(target.getBucket(), key, format.getContentType());

        // Construct the final URL where the file will be publicly accessible after upload
        String finalUrl = target.getPublicUrl() + "/" + key;

        return new UploadUrlResponse(key, uploadUrl, finalUrl);
    }
}
