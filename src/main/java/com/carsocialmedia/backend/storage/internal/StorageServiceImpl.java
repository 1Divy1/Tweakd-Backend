package com.carsocialmedia.backend.storage.internal;

import com.carsocialmedia.backend.storage.StorageBucket;
import com.carsocialmedia.backend.storage.StorageService;
import com.carsocialmedia.backend.storage.dto.ModificationUploadUrlsResponse;
import com.carsocialmedia.backend.storage.dto.UploadUrlResponse;
import com.carsocialmedia.backend.storage.internal.cloudflare.PresignedUrlGenerator;
import com.carsocialmedia.backend.storage.internal.ModificationUploadRequest;
import com.carsocialmedia.backend.storage.internal.cloudflare.R2Config;
import com.carsocialmedia.backend.storage.internal.enums.FileFormat;
import com.carsocialmedia.backend.storage.internal.enums.ModificationPhase;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.util.List;
import java.util.UUID;

@Service
public class StorageServiceImpl implements StorageService {

    private final PresignedUrlGenerator presigner;
    private final R2Config config;
    private final S3Client s3Client;

    StorageServiceImpl(PresignedUrlGenerator presigner, R2Config config, S3Client s3Client) {
        this.presigner = presigner;
        this.config = config;
        this.s3Client = s3Client;
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

    // cars/{carId}/modifications/{modId}/{phase}/{uuid}.{ext}  (one per item)
    @Override
    public ModificationUploadUrlsResponse modificationBatchUploadUrlRequest(UUID carId, UUID modId, List<ModificationUploadRequest.MediaItem> files) {
        List<ModificationUploadUrlsResponse.Item> uploads = files.stream()
                .map(file -> {
                    String key = "cars/" + carId +
                            "/modifications/" + modId +
                            "/" + file.phase().folder() +
                            "/" + UUID.randomUUID() + file.format().getExtension();
                    String uploadUrl = presigner.generateUploadUrl(
                            config.getGarage().getBucket(), key, file.format().getContentType());
                    return new ModificationUploadUrlsResponse.Item(key, uploadUrl, file.phase().folder());
                })
                .toList();
        return new ModificationUploadUrlsResponse(uploads);
    }

    @Override
    public String publicUrl(StorageBucket bucket, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return config.target(bucket).getPublicUrl() + "/" + key;
    }

    @Override
    public void deleteByKeys(StorageBucket bucket, List<String> keys) {
        if (keys == null || keys.isEmpty()) return;

        List<ObjectIdentifier> objects = keys.stream()
                .filter(key -> key != null && !key.isBlank())
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();

        if (objects.isEmpty()) return;

        s3Client.deleteObjects(r -> r
                .bucket(config.target(bucket).getBucket())
                .delete(d -> d.objects(objects))
        );
    }

    // ========== HELPERS ==========

    private UploadUrlResponse buildUploadUrlResponse(R2Config.BucketTarget target, String key, FileFormat format) {
        String uploadUrl = presigner.generateUploadUrl(target.getBucket(), key, format.getContentType());
        return new UploadUrlResponse(key, uploadUrl);
    }
}
