package com.carsocialmedia.backend.storage.dto;

/**
 * Response for a single presigned upload-URL request.
 *
 * Flutter PUTs the file to {@code uploadUrl}, then persists {@code key} (the bucket-relative
 * R2 object key) via the owning module. Only the key is stored — the public URL is built by
 * the backend on read, so the bucket/domain can change without a data migration.
 *
 * @param key       the R2 object key (bucket-relative path); this is what gets persisted
 * @param uploadUrl the presigned PUT URL for uploading directly to R2
 */
public record UploadUrlResponse(String key, String uploadUrl) {}