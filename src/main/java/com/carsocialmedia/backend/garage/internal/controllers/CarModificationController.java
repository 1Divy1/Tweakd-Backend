package com.carsocialmedia.backend.garage.internal.controllers;

import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.response.AddModificationResponse;
import com.carsocialmedia.backend.garage.dto.CarModificationDto;
import com.carsocialmedia.backend.garage.dto.request.CarModificationRequest;
import com.carsocialmedia.backend.garage.dto.request.UpdateModificationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST endpoints for car modifications (upgrades, parts, customizations).
 *
 * Modifications record changes or upgrades made to a car, including installation
 * date, cost, and category. Price visibility respects the owner's privacy setting
 * (isPricePublic flag).
 */
@RestController
@RequestMapping("/api/v1/garage/cars/{carId}/modifications")
public class CarModificationController {

    private final GarageService garageService;

    public CarModificationController(GarageService garageService) {
        this.garageService = garageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddModificationResponse addModification(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID carId,
                                                   @Valid @RequestBody CarModificationRequest request) {
        return garageService.addModification(jwt.getSubject(), carId, request);
    }

    @PatchMapping("/{modificationId}")
    public CarModificationDto patchModification(@AuthenticationPrincipal Jwt jwt,
                                                @PathVariable UUID carId,
                                                @PathVariable UUID modificationId,
                                                @Valid @RequestBody UpdateModificationRequest request) {
        return garageService.patchModification(jwt.getSubject(), carId, modificationId, request);
    }

    @DeleteMapping("/{modificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModification(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID carId,
                                   @PathVariable UUID modificationId) {
        garageService.deleteModification(jwt.getSubject(), carId, modificationId);
    }
}