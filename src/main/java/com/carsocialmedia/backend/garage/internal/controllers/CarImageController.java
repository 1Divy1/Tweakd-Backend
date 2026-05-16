package com.carsocialmedia.backend.garage.internal.controllers;

import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.CarImageDownloadRequest;
import com.carsocialmedia.backend.garage.dto.CarImageDto;
import com.carsocialmedia.backend.garage.dto.SignedUrlResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for car photo storage: gallery listing/deletion and presigned
 * download URLs. Upload URLs are issued by {@code POST /api/v1/garage/cars} as part
 * of car creation, so there is no standalone upload-url endpoint here.
 */
@RestController
@RequestMapping("/api/v1/garage")
public class CarImageController {

    private final GarageService garageService;

    public CarImageController(GarageService garageService) {
        this.garageService = garageService;
    }

    @GetMapping("/cars/{carId}/images")
    public List<CarImageDto> listCarImages(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID carId) {
        return garageService.listCarImages(jwt.getSubject(), carId);
    }

    @DeleteMapping("/cars/{carId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCarImage(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID carId,
                               @PathVariable UUID imageId) {
        garageService.deleteCarImage(jwt.getSubject(), carId, imageId);
    }

    @PostMapping("/storage/download-url")
    public SignedUrlResponse generateDownloadUrl(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody CarImageDownloadRequest request) {
        return new SignedUrlResponse(
                garageService.generateCarImageDownloadUrl(jwt.getSubject(), request.storagePath()));
    }
}
