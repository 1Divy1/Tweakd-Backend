package com.carsocialmedia.backend.storage;

import java.util.List;

/**
 * Thin wrapper around Supabase Storage for minting presigned URLs.
 *
 * No domain knowledge: callers (typically the garage module) are responsible for
 * validating ownership and computing the object path. This module only translates
 * a path to a signed URL.
 */
public interface StorageService {

    /**
     * Returns a presigned upload URL for the given object path. The caller PUTs the
     * file directly to the returned URL.
     *
     * @param path object path inside the configured bucket (no leading slash)
     * @return a Supabase presigned upload URL
     */
    String createUploadUrl(String path);

    /**
     * Returns a short-lived presigned download URL for the given object path.
     *
     * @param path object path inside the configured bucket (no leading slash)
     * @return a Supabase signed download URL
     */
    String createDownloadUrl(String path);

    /**
     * Deletes the given objects from storage. A best-effort call — callers should
     * not rely on success for correctness (DB rows are the source of truth).
     *
     * @param paths object paths inside the configured bucket (no leading slash)
     */
    void deleteObjects(List<String> paths);
}
