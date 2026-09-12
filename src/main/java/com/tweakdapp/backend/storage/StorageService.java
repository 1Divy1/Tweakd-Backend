package com.tweakdapp.backend.storage;

import com.tweakdapp.backend.storage.dto.ModificationUploadUrlsResponse;
import com.tweakdapp.backend.storage.dto.PostImagesUploadUrlsResponse;
import com.tweakdapp.backend.storage.dto.UploadUrlResponse;
import com.tweakdapp.backend.storage.internal.ModificationUploadRequest;
import com.tweakdapp.backend.storage.internal.enums.FileFormat;
import com.tweakdapp.backend.storage.internal.enums.ModificationPhase;

import java.util.List;
import java.util.UUID;

public interface StorageService {
    UploadUrlResponse coverUploadUrlRequest(UUID carId);
    UploadUrlResponse galleryUploadUrlRequest(UUID carId);

    /**
     * Issues a presigned PUT URL for the given user's avatar image. Flutter uploads directly to R2
     * and then sends the returned {@code key} back to the profile module to persist.
     *
     * @param userId the profile the avatar belongs to (used to namespace the R2 key)
     * @return a {@code {key, uploadUrl}} slot
     */
    UploadUrlResponse avatarUploadUrlRequest(UUID userId);

    /**
     * Issues a batch of presigned PUT URLs for a post's images in one call. Flutter uploads each
     * image directly to R2 and then sends the returned {@code key}s back to the posts module to
     * persist. Replaces N single-image round-trips for a multi-image post.
     *
     * @param postId the post the images belong to (used to namespace the R2 keys)
     * @param count how many upload slots to mint (1–10)
     * @return one {@code {key, uploadUrl}} slot per requested image
     */
    PostImagesUploadUrlsResponse postImagesUploadUrlRequest(UUID postId, int count);

    /**
     * Issues a presigned PUT URL for a car event's cover image. Follows the same create-then-attach
     * flow as posts: the event row is created first so its id exists, Flutter uploads the cover
     * straight to R2, then sends the returned {@code key} back to the events module to persist.
     *
     * @param eventId the event the cover belongs to (used to namespace the R2 key)
     * @return a {@code {key, uploadUrl}} slot
     */
    UploadUrlResponse eventCoverUploadUrlRequest(UUID eventId);
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
     * The bucket's public URL prefix, with no trailing slash — {@code publicUrl(bucket, key)} is
     * this plus {@code "/" + key}.
     *
     * <p>Exists for the dashboard's badge form, where a staff member types an R2 object key by hand
     * (badge artwork is uploaded out of band) and needs to see the resulting image before saving.
     * Fetching the prefix once beats a round trip per keystroke. It is not a secret: it is the same
     * host every badge image in the app is already served from.
     */
    String publicBaseUrl(StorageBucket bucket);

    /**
     * Deletes objects from R2 by their bucket-relative keys. Null/blank keys are skipped.
     *
     * @param bucket the logical bucket the keys belong to
     * @param keys the R2 object keys to delete (empty list is a no-op)
     */
    void deleteByKeys(StorageBucket bucket, List<String> keys);
}
