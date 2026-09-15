package com.tweakdapp.backend.storage.internal;

import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import com.tweakdapp.backend.storage.UploadAccessPolicy;
import com.tweakdapp.backend.storage.UploadTarget;
import com.tweakdapp.backend.storage.dto.ModificationUploadUrlsResponse;
import com.tweakdapp.backend.storage.dto.PostImagesUploadUrlsResponse;
import com.tweakdapp.backend.storage.dto.UploadUrlResponse;
import com.tweakdapp.backend.storage.internal.cloudflare.PresignedUrlGenerator;
import com.tweakdapp.backend.storage.internal.ModificationUploadRequest;
import com.tweakdapp.backend.storage.internal.cloudflare.R2Config;
import com.tweakdapp.backend.storage.internal.enums.FileFormat;
import com.tweakdapp.backend.storage.internal.enums.ModificationPhase;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StorageServiceImpl implements StorageService {

    private final PresignedUrlGenerator presigner;
    private final R2Config config;
    private final S3Client s3Client;
    private final Map<UploadTarget, UploadAccessPolicy> accessPolicies = new EnumMap<>(UploadTarget.class);

    StorageServiceImpl(PresignedUrlGenerator presigner, R2Config config, S3Client s3Client,
                       List<UploadAccessPolicy> accessPolicies) {
        this.presigner = presigner;
        this.config = config;
        this.s3Client = s3Client;
        accessPolicies.forEach(policy -> this.accessPolicies.put(policy.target(), policy));
    }

    // === AVATARS ===
    // avatars/{userId}/{uuid}.webp
    @Override
    public UploadUrlResponse avatarUploadUrlRequest(UUID userId) {
        String key = "avatars/" + userId +
                "/" + UUID.randomUUID() + FileFormat.WEBP.getExtension();

        return buildUploadUrlResponse(config.getAvatars(), key, FileFormat.WEBP);
    }

    // === GARAGE ===
    // cars/{carId}/cover/{uuid}.webp
    // A random suffix rather than a fixed "cover.webp": a URL minted for the cover can never overwrite
    // the one that is live, and a replaced cover gets a key the CDN has never cached.
    @Override
    public UploadUrlResponse coverUploadUrlRequest(UUID userId, UUID carId) {
        requireUploadAccess(UploadTarget.CAR, userId, carId);
        String key = "cars/" + carId +
                "/cover/" + UUID.randomUUID() + FileFormat.WEBP.getExtension();

        return buildUploadUrlResponse(config.getGarage(), key, FileFormat.WEBP);
    }

    // TODO: consider modifying the file extension to allow videos too
    // cars/{carId}/gallery/{uuid}.webp
    @Override
    public UploadUrlResponse galleryUploadUrlRequest(UUID userId, UUID carId) {
        requireUploadAccess(UploadTarget.CAR, userId, carId);
        String key = "cars/" + carId +
                "/gallery/" + UUID.randomUUID() + FileFormat.WEBP.getExtension();

        return buildUploadUrlResponse(config.getGarage(), key, FileFormat.WEBP);
    }

    // cars/{carId}/modifications/{modId}/{phase}/{uuid}.{ext}
    // Owning the car is enough: a modId from another car only yields a key under the caller's own car,
    // which the garage module refuses to attach to that other car's modification.
    @Override
    public UploadUrlResponse modificationUploadUrlRequest(UUID userId, UUID carId, UUID modId, ModificationPhase phase, FileFormat format) {
        requireUploadAccess(UploadTarget.CAR, userId, carId);
        String key = "cars/" + carId +
                "/modifications/" + modId +
                "/" + phase.folder() +
                "/" + UUID.randomUUID() + format.getExtension();

        return buildUploadUrlResponse(config.getGarage(), key, format);
    }

    // cars/{carId}/modifications/{modId}/{phase}/{uuid}.{ext}  (one per item)
    @Override
    public ModificationUploadUrlsResponse modificationBatchUploadUrlRequest(UUID userId, UUID carId, UUID modId, List<ModificationUploadRequest.MediaItem> files) {
        requireUploadAccess(UploadTarget.CAR, userId, carId);
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

    // === POSTS ===
    // posts/{postId}/{uuid}.webp  (one per requested image)
    @Override
    public PostImagesUploadUrlsResponse postImagesUploadUrlRequest(UUID userId, UUID postId, int count) {
        requireUploadAccess(UploadTarget.POST, userId, postId);
        List<UploadUrlResponse> uploads = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String key = "posts/" + postId +
                    "/" + UUID.randomUUID() + FileFormat.WEBP.getExtension();
            uploads.add(buildUploadUrlResponse(config.getPosts(), key, FileFormat.WEBP));
        }
        return new PostImagesUploadUrlsResponse(uploads);
    }

    // === MAP EVENTS ===
    // events/{eventId}/{uuid}.webp
    // A random suffix rather than a fixed "cover.webp": replacing a cover writes a new key, so the
    // CDN can never serve a stale copy of the old one, and the previous object can be deleted.
    @Override
    public UploadUrlResponse eventCoverUploadUrlRequest(UUID userId, UUID eventId) {
        requireUploadAccess(UploadTarget.MAP_EVENT, userId, eventId);
        String key = "events/" + eventId +
                "/" + UUID.randomUUID() + FileFormat.WEBP.getExtension();

        return buildUploadUrlResponse(config.getMapEvents(), key, FileFormat.WEBP);
    }

    @Override
    public String publicUrl(StorageBucket bucket, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return config.target(bucket).getPublicUrl() + "/" + key;
    }

    @Override
    public String publicBaseUrl(StorageBucket bucket) {
        return config.target(bucket).getPublicUrl();
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
    private void requireUploadAccess(UploadTarget target, UUID userId, UUID resourceId) {
        UploadAccessPolicy policy = accessPolicies.get(target);
        if (policy == null) {
            // Fail closed: a target nobody guards must never get upload URLs.
            throw new IllegalStateException("No upload access policy registered for " + target);
        }
        policy.requireUploadAccess(userId, resourceId);
    }

    private UploadUrlResponse buildUploadUrlResponse(
            R2Config.BucketTarget target,
            String key,
            FileFormat format
    ) {
        String uploadUrl = presigner.generateUploadUrl(target.getBucket(), key, format.getContentType());
        return new UploadUrlResponse(key, uploadUrl);
    }
}
