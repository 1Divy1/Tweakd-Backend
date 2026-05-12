package com.carsocialmedia.backend.garage.internal.controllers;

import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.GarageDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/garage")
public class GarageController {

    private final GarageService garageService;

    public GarageController(GarageService garageService) {
        this.garageService = garageService;
    }

    @GetMapping("/me")
    public GarageDto getMyGarage(@AuthenticationPrincipal Jwt jwt) {
        return garageService.getMyGarage(jwt.getSubject());
    }

    @GetMapping("/by-username/{username}")
    public GarageDto getGarageByUsername(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable String username) {
        return garageService.getGarageByUsername(jwt.getSubject(), username);
    }
}
