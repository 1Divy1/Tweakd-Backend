package com.tweakdapp.backend.garage.internal.controllers;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarShareDto;
import com.tweakdapp.backend.garage.dto.CarShareQrDto;
import com.tweakdapp.backend.garage.dto.request.ShareLinkUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The owner's share surface for one car: the link, its pause switch, and the printable QR.
 *
 * <p>Every route here is owner-only. Cars themselves are public to any signed-in user, but whether
 * a car is shared, what its code is, and how many people have scanned it are the owner's business
 * alone — so a non-owner gets 403 rather than a read-only view.
 */
@RestController
@RequestMapping("/api/v1/garage/cars/{carId}/share")
public class CarShareController {

    private final GarageService garageService;

    public CarShareController(GarageService garageService) {
        this.garageService = garageService;
    }

    /**
     * The car's share link, creating one on first call. 200 either way — the app opens the share
     * sheet with this and does not care whether the code is new, only that it is stable.
     */
    @PostMapping
    public CarShareDto createShareLink(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID carId) {
        return garageService.ensureShareLink(jwt.getSubject(), carId);
    }

    /** The car's share link, or 404 if it has never been shared. */
    @GetMapping
    public CarShareDto getShareLink(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID carId) {
        return garageService.getShareLink(jwt.getSubject(), carId);
    }

    /** Pauses or resumes the link. The code is unchanged, so a printed sticker survives both. */
    @PatchMapping
    public CarShareDto updateShareLink(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID carId,
                                       @Valid @RequestBody ShareLinkUpdateRequest request) {
        return garageService.setShareLinkEnabled(jwt.getSubject(), carId, request.enabled());
    }

    /**
     * The share URL as a QR code, in SVG, ready to print.
     *
     * <p>{@code no-store} because the response is per-owner and the app already caches the bytes it
     * hands to the share sheet; the {@code attachment} disposition is what makes a browser save it
     * as {@code tweakd-{code}.svg} rather than render it inline.
     */
    @GetMapping(value = "/qr.svg", produces = "image/svg+xml")
    public ResponseEntity<byte[]> getShareQr(@AuthenticationPrincipal Jwt jwt,
                                             @PathVariable UUID carId) {
        CarShareQrDto qr = garageService.renderShareQrSvg(jwt.getSubject(), carId);
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tweakd-" + qr.code() + ".svg\"")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(qr.svg());
    }
}
