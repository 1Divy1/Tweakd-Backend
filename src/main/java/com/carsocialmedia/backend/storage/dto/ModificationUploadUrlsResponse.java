package com.carsocialmedia.backend.storage.dto;

import java.util.List;

/**
 * Response for a batch modification media upload-URL request.
 *
 * Each item corresponds to one file in the request, in the same order.
 * Flutter uses {@code uploadUrl} to PUT the file directly to R2, then passes
 * {@code key} and {@code phase} to the modification PATCH endpoint. Only the key is
 * persisted; the public URL is built by the backend on read.
 *
 * @param uploads one entry per requested file
 */
public record ModificationUploadUrlsResponse(List<Item> uploads) {

    /**
     * @param key       the R2 object key (bucket-relative path); this is what gets persisted
     * @param uploadUrl the presigned PUT URL for uploading directly to R2
     * @param phase     "before" or "after"
     */
    public record Item(String key, String uploadUrl, String phase) {}
}
