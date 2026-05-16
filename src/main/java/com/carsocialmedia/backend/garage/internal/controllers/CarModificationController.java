package com.carsocialmedia.backend.garage.internal.controllers;

import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.AddModificationResponse;
import com.carsocialmedia.backend.garage.dto.CarModificationDto;
import com.carsocialmedia.backend.garage.dto.CarModificationRequest;
import com.carsocialmedia.backend.garage.dto.ModificationUploadSlots;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST endpoints for car modifications (upgrades, parts, customizations).
 *
 * Modifications record changes or upgrades made to a car, including before/after
 * images, installation date, cost, and category. Price visibility respects the
 * owner's privacy setting (isPricePublic flag).
 */
@RestController
@RequestMapping("/api/v1/garage/cars/{carId}/modifications")
public class CarModificationController {

    private final GarageService garageService;

    public CarModificationController(GarageService garageService) {
        this.garageService = garageService;
    }

    /**
     * Adds a new modification to a car owned by the current user.
     *
     * @param carId the car ID
     * @param request the modification details (category, title, images, date, price, etc.)
     * @return the newly created modification
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     * @throws InvalidReferenceException if the modification category does not exist
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddModificationResponse addModification(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable UUID carId,
                                                   @Valid @RequestBody CarModificationRequest request) {
        return garageService.addModification(jwt.getSubject(), carId, request);
    }

    /**
     * Updates a modification on a car owned by the current user (full replacement).
     *
     * @param carId the car ID
     * @param modificationId the modification ID
     * @param request the updated modification details
     * @return the updated modification
     * @throws CarNotFoundException if the car does not exist
     * @throws CarModificationNotFoundException if the modification does not exist or
     *         does not belong to the specified car
     * @throws NotCarOwnerException if the current user is not the car owner
     * @throws InvalidReferenceException if the category ID is invalid
     */
    @PutMapping("/{modificationId}")
    public CarModificationDto updateModification(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID carId,
                                                 @PathVariable UUID modificationId,
                                                 @Valid @RequestBody CarModificationRequest request) {
        return garageService.updateModification(jwt.getSubject(), carId, modificationId, request);
    }

    /**
     * Deletes a modification from a car owned by the current user.
     *
     * @param carId the car ID
     * @param modificationId the modification ID
     * @throws CarNotFoundException if the car does not exist
     * @throws CarModificationNotFoundException if the modification does not exist or
     *         does not belong to the specified car
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    @DeleteMapping("/{modificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteModification(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID carId,
                                   @PathVariable UUID modificationId) {
        garageService.deleteModification(jwt.getSubject(), carId, modificationId);
    }

    /**
     * Returns fresh presigned upload URLs for a modification's before/after images.
     * Use this when the original URLs from creation have expired or the images need
     * to be replaced.
     *
     * @param carId the car ID
     * @param modificationId the modification ID
     * @return before and after upload slots with new presigned URLs
     * @throws CarModificationNotFoundException if the modification does not exist or
     *         does not belong to the specified car
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    @PostMapping("/{modificationId}/upload-urls")
    public ModificationUploadSlots refreshModificationUploadUrls(@AuthenticationPrincipal Jwt jwt,
                                                                  @PathVariable UUID carId,
                                                                  @PathVariable UUID modificationId) {
        return garageService.refreshModificationUploadUrls(jwt.getSubject(), carId, modificationId);
    }
}
