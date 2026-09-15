package com.tweakdapp.backend.storage.internal;

import com.tweakdapp.backend.storage.StorageService;
import com.tweakdapp.backend.storage.dto.ModificationUploadUrlsResponse;
import com.tweakdapp.backend.storage.dto.PostImagesUploadUrlsResponse;
import com.tweakdapp.backend.storage.dto.UploadUrlResponse;
import com.tweakdapp.backend.storage.internal.enums.FileFormat;
import com.tweakdapp.backend.storage.internal.enums.ModificationPhase;
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
        return storageService.avatarUploadUrlRequest(userId(jwt));
    }

    // ----- CARS -----
    @GetMapping("/cars/{carId}/cover")
    public UploadUrlResponse cover(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID carId) {
        return storageService.coverUploadUrlRequest(userId(jwt), carId);
    }

    @GetMapping("/cars/{carId}/gallery")
    public UploadUrlResponse gallery(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID carId) {
        return storageService.galleryUploadUrlRequest(userId(jwt), carId);
    }

    @GetMapping("/cars/{carId}/modifications/{modId}")
    public UploadUrlResponse modification(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID carId,
            @PathVariable UUID modId,
            @RequestParam ModificationPhase phase,
            @RequestParam FileFormat format
    ) {
        return storageService.modificationUploadUrlRequest(userId(jwt), carId, modId, phase, format);
    }

    @PostMapping("/cars/{carId}/modifications/{modId}/upload-urls")
    public ModificationUploadUrlsResponse modificationBatch(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID carId,
            @PathVariable UUID modId,
            @Valid @RequestBody ModificationUploadRequest request
    ) {
        return storageService.modificationBatchUploadUrlRequest(userId(jwt), carId, modId, request.files());
    }

    // ----- MAP EVENTS -----
    @GetMapping("/events/{eventId}/cover")
    public UploadUrlResponse eventCover(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return storageService.eventCoverUploadUrlRequest(userId(jwt), eventId);
    }

    // ----- POSTS -----
    @PostMapping("/posts/{postId}/upload-urls")
    public PostImagesUploadUrlsResponse postImages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID postId,
            @Valid @RequestBody PostImagesUploadRequest request
    ) {
        return storageService.postImagesUploadUrlRequest(userId(jwt), postId, request.count());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
