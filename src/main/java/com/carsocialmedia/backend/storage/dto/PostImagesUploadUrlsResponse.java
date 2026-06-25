package com.carsocialmedia.backend.storage.dto;

import java.util.List;

/**
 * Response for a batch post-image upload-URL request.
 *
 * Each entry is one presigned slot: Flutter PUTs an image to {@code uploadUrl}, then sends the
 * corresponding {@code key} back to the posts module. Only keys are persisted; public URLs are
 * built on read. The order of {@code uploads} is not significant — the client decides the final
 * image order when it sends the keys to {@code PATCH /api/v1/posts/{postId}/images}.
 *
 * @param uploads one {key, uploadUrl} slot per requested image
 */
public record PostImagesUploadUrlsResponse(List<UploadUrlResponse> uploads) {}