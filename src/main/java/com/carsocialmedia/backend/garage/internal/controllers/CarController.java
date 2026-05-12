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

@RestController
@RequestMapping("/api/v1/garage/cars")
public class CarController {

    private final GarageService garageService;

    public CarController(GarageService garageService) {
        this.garageService = garageService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CarDto addCar(@AuthenticationPrincipal Jwt jwt,
                         @Valid @RequestBody CarRequest request) {
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
}
