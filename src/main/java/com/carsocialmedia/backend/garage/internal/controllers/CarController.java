package com.carsocialmedia.backend.garage.internal.controllers;

import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.CarDto;
import com.carsocialmedia.backend.garage.dto.CarRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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

    /**
     * Adds a new car to the current user's garage.
     *
     * @param request the car details (brand, model, drivetrain, color, specs, etc.)
     * @return the newly created car with all resolved references and an empty modifications list
     * @throws InvalidReferenceException if any referenced ID (brand, model, drivetrain, etc.)
     *         does not exist or is inconsistent (e.g., model doesn't belong to brand)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CarDto addCar(@AuthenticationPrincipal Jwt jwt,
                         @Valid @RequestBody CarRequest request) {
        return garageService.addCar(jwt.getSubject(), request);
    }

    /**
     * Updates a car owned by the current user (full replacement).
     *
     * @param carId the car ID
     * @param request the updated car details
     * @return the updated car with resolved references and current modifications
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     * @throws InvalidReferenceException if any referenced ID is invalid or inconsistent
     */
    @PutMapping("/{carId}")
    public CarDto updateCar(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID carId,
                            @Valid @RequestBody CarRequest request) {
        return garageService.updateCar(jwt.getSubject(), carId, request);
    }

    /**
     * Deletes a car owned by the current user. Cascades to remove all modifications.
     *
     * @param carId the car ID
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    @DeleteMapping("/{carId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCar(@AuthenticationPrincipal Jwt jwt,
                          @PathVariable UUID carId) {
        garageService.deleteCar(jwt.getSubject(), carId);
    }

    /**
     * Retrieves a car with full details and all modifications. If the viewer is not
     * the owner, visibility is subject to the owner's garage privacy settings.
     *
     * @param carId the car ID
     * @return the car with all details and modifications
     * @throws CarNotFoundException if the car does not exist
     * @throws PrivateGarageException if the garage is private and the viewer is not allowed access
     */
    @GetMapping("/{carId}")
    public CarDto getCar(@AuthenticationPrincipal Jwt jwt,
                         @PathVariable UUID carId) {
        return garageService.getCar(jwt.getSubject(), carId);
    }
}
