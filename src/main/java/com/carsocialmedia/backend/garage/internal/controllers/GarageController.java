package com.carsocialmedia.backend.garage.internal.controllers;

import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.GarageDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for garage views and queries.
 *
 * A garage is a user's collection of cars and their modifications. This controller
 * exposes endpoints to retrieve the current user's garage or another user's garage
 * (subject to privacy rules tied to follow/profile settings).
 */
@RestController
@RequestMapping("/api/v1/garage")
public class GarageController {

    private final GarageService garageService;

    public GarageController(GarageService garageService) {
        this.garageService = garageService;
    }

    /**
     * Gets the current user's garage (always visible, no privacy gating).
     *
     * @return the garage with a list of cars and their summaries
     */
    @GetMapping("/me")
    public GarageDto getMyGarage(@AuthenticationPrincipal Jwt jwt) {
        GarageDto garage = garageService.getMyGarage(jwt.getSubject());
        System.out.println("Garage: " + garage);
        return garage;
    }

    /**
     * Gets another user's garage by username. Privacy rules mirror the profile / follow
     * social-graph rules: private profiles' garages are visible only to the owner or
     * accepted followers. Returns 403 if access is denied.
     *
     * @param username the username of the garage owner
     * @return the garage with a list of cars and their summaries
     * @throws PrivateGarageException if the garage owner is private and the current user
     *         is not an accepted follower
     */
    @GetMapping("/by-username/{username}")
    public GarageDto getGarageByUsername(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable String username) {
        return garageService.getGarageByUsername(jwt.getSubject(), username);
    }
}
