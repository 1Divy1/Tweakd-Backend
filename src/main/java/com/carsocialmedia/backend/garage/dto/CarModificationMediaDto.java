package com.carsocialmedia.backend.garage.dto;

/**
 * A single media item (image or video) attached to a modification.
 *
 * @param key   the bucket-relative R2 object key (sent back to delete this item)
 * @param url   public R2 URL of the file (built by the backend from the stored key)
 * @param type  "image" or "video"
 * @param phase "before" or "after"
 */
public record CarModificationMediaDto(String key, String url, String type, String phase) {}
