package com.tweakdapp.backend.garage.internal.controllers;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.DreamCarDto;
import com.tweakdapp.backend.garage.dto.request.DreamCarRequest;
import com.tweakdapp.backend.garage.dto.request.DreamCarRequestBody;
import com.tweakdapp.backend.shared.ratelimit.RateLimited;
import com.tweakdapp.backend.shared.ratelimit.RateLimits;
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

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for the current user's dream cars (wishlist). Every operation is scoped
 * to the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/garage/dream-cars")
public class DreamCarController {

    private final GarageService garageService;

    public DreamCarController(GarageService garageService) {
        this.garageService = garageService;
    }

    @GetMapping
    public List<DreamCarDto> listDreamCars(@AuthenticationPrincipal Jwt jwt) {
        return garageService.listDreamCars(jwt.getSubject());
    }

    @PostMapping
    @RateLimited(RateLimits.GARAGE_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    public List<DreamCarDto> addDreamCar(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody DreamCarRequest request) {
        return garageService.addDreamCar(jwt.getSubject(), request);
    }

    @PutMapping("/{dreamCarId}")
    @RateLimited(RateLimits.GARAGE_WRITE)
    public DreamCarDto updateDreamCar(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable UUID dreamCarId,
                                      @Valid @RequestBody DreamCarRequestBody body) {
        return garageService.updateDreamCar(jwt.getSubject(), dreamCarId, body);
    }

    @DeleteMapping("/{dreamCarId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDreamCar(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID dreamCarId) {
        garageService.deleteDreamCar(jwt.getSubject(), dreamCarId);
    }
}