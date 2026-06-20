package com.carsocialmedia.backend.garage.dto;

/**
 * A single media item (image or video) attached to a modification.
 *
 * @param url   public R2 URL of the file
 * @param type  "image" or "video"
 * @param phase "before" or "after"
 */
public record CarModificationMediaDto(String url, String type, String phase) {}
