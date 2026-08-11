package com.carsocialmedia.backend.storage.internal;

import com.carsocialmedia.backend.storage.StorageService;
import com.carsocialmedia.backend.storage.dto.ModificationUploadUrlsResponse;
import com.carsocialmedia.backend.storage.dto.PostImagesUploadUrlsResponse;
import com.carsocialmedia.backend.storage.dto.UploadUrlResponse;
import com.carsocialmedia.backend.storage.internal.enums.FileFormat;
import com.carsocialmedia.backend.storage.internal.enums.ModificationPhase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;

    // ----- AVATARS -----
    @GetMapping("/avatar")
    public UploadUrlResponse avatar(@AuthenticationPrincipal Jwt jwt) {
        return storageService.avatarUploadUrlRequest(UUID.fromString(jwt.getSubject()));
    }

    // ----- CARS -----
    @GetMapping("/cars/{carId}/cover")
    public UploadUrlResponse cover(@PathVariable UUID carId) {
        return storageService.coverUploadUrlRequest(carId);
    }

    @GetMapping("/cars/{carId}/gallery")
    public UploadUrlResponse gallery(@PathVariable UUID carId) {
        return storageService.galleryUploadUrlRequest(carId);
    }

    @GetMapping("/cars/{carId}/modifications/{modId}")
    public UploadUrlResponse modification(
            @PathVariable UUID carId,
            @PathVariable UUID modId,
            @RequestParam ModificationPhase phase,
            @RequestParam FileFormat format
    ) {
        return storageService.modificationUploadUrlRequest(carId, modId, phase, format);
    }

    @PostMapping("/cars/{carId}/modifications/{modId}/upload-urls")
    public ModificationUploadUrlsResponse modificationBatch(
            @PathVariable UUID carId,
            @PathVariable UUID modId,
            @Valid @RequestBody ModificationUploadRequest request
    ) {
        return storageService.modificationBatchUploadUrlRequest(carId, modId, request.files());
    }

    // ----- MAP EVENTS -----
    @GetMapping("/events/{eventId}/cover")
    public UploadUrlResponse eventCover(@PathVariable UUID eventId) {
        return storageService.eventCoverUploadUrlRequest(eventId);
    }

    // ----- POSTS -----
    @PostMapping("/posts/{postId}/upload-urls")
    public PostImagesUploadUrlsResponse postImages(
            @PathVariable UUID postId,
            @Valid @RequestBody PostImagesUploadRequest request
    ) {
        return storageService.postImagesUploadUrlRequest(postId, request.count());
    }
}
