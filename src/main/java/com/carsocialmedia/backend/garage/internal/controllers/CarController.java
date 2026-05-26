package com.carsocialmedia.backend.garage.internal.controllers;

import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.CarDto;
import com.carsocialmedia.backend.garage.dto.request.CarRequest;
import com.carsocialmedia.backend.garage.dto.request.CreateCarRequest;
import com.carsocialmedia.backend.garage.dto.request.GalleryUrlsRequest;
import com.carsocialmedia.backend.garage.dto.response.CreateCarResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for car operations within a garage.
 *
 * Cars belong to a garage and contain detailed specifications along with
 * a list of modifications. This controller provides endpoints to add, update,
 * retrieve, and delete cars owned by the current user.
 */
@RestController
@RequestMapping("/api/v1/garage/cars")
public class CarController {

    private final GarageService garageService;

    public CarController(GarageService garageService) {
        this.garageService = garageService;
    }

    // ----- BASIC CAR ENDPOINTS -----

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateCarResponse addCar(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody CreateCarRequest request) {
        return garageService.addCar(jwt.getSubject(), request);
    }

    @PutMapping("/{carId}")
    public CarDto updateCar(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID carId,
                            @Valid @RequestBody CarRequest request) {
        return garageService.updateCar(jwt.getSubject(), carId, request);
    }

    @DeleteMapping("/{carId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCar(@AuthenticationPrincipal Jwt jwt,
                          @PathVariable UUID carId) {
        garageService.deleteCar(jwt.getSubject(), carId);
    }

    @GetMapping("/{carId}")
    public CarDto getCar(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable UUID carId) {
        return garageService.getCar(jwt.getSubject(), carId);
    }

    // ----- MEDIA ENDPOINTS -----

    @PatchMapping("/{carId}/cover")
    public void updateCarCover(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID carId,
            @RequestParam("coverImageUrl") String coverImageUrl
    ) {
        garageService.saveCarCoverImageUrl(jwt.getSubject(), carId, coverImageUrl);
    }

    @PatchMapping("/{carId}/gallery")
    public void updateCarGallery(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID carId,
            @Valid @RequestBody GalleryUrlsRequest request
    ) {
        garageService.saveGalleryImageUrls(jwt.getSubject(), carId, request.urls());
    }
}