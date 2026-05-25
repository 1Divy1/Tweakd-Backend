package com.carsocialmedia.backend.storage.internal;

import com.carsocialmedia.backend.storage.StorageService;
import com.carsocialmedia.backend.storage.dto.UploadUrlResponse;
import com.carsocialmedia.backend.storage.internal.cloudflare.PresignedUrlGenerator;
import com.carsocialmedia.backend.storage.internal.cloudflare.R2Config;
import com.carsocialmedia.backend.storage.internal.enums.FileFormat;
import com.carsocialmedia.backend.storage.internal.enums.ModificationPhase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.util.List;
import java.util.UUID;

@Service
public class StorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageServiceImpl.class);

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

    @Override
    public void deleteObjects(List<String> urls) {
        String garagePublicUrl = config.getGarage().getPublicUrl();
        String garageBucket    = config.getGarage().getBucket();

        List<ObjectIdentifier> keys = urls.stream()
                .filter(url -> {
                    if (!url.startsWith(garagePublicUrl + "/")) {
                        log.warn("Skipping deletion — URL does not match any known bucket: {}", url);
                        return false;
                    }
                    return true;
                })
                .map(url -> ObjectIdentifier.builder()
                        .key(url.substring(garagePublicUrl.length() + 1))
                        .build())
                .toList();

        if (keys.isEmpty()) return;

        s3Client.deleteObjects(r -> r
                .bucket(garageBucket)
                .delete(d -> d.objects(keys))
        );
    }

    // ========== HELPERS ==========

    private UploadUrlResponse buildUploadUrlResponse(R2Config.BucketTarget target, String key, FileFormat format) {
        String uploadUrl = presigner.generateUploadUrl(target.getBucket(), key, format.getContentType());
        String finalUrl  = target.getPublicUrl() + "/" + key;
        return new UploadUrlResponse(key, uploadUrl, finalUrl);
    }
}
