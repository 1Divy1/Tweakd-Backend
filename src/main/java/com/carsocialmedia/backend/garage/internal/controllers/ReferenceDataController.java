package com.carsocialmedia.backend.garage.internal.controllers;

import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.CarBrandDto;
import com.carsocialmedia.backend.garage.dto.CarColorDto;
import com.carsocialmedia.backend.garage.dto.CarDistanceUnitDto;
import com.carsocialmedia.backend.garage.dto.CarDrivetrainDto;
import com.carsocialmedia.backend.garage.dto.CarModCategoryDto;
import com.carsocialmedia.backend.garage.dto.CarModelDto;
import com.carsocialmedia.backend.garage.dto.CarStatusOptionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/garage/reference")
public class ReferenceDataController {

    private final GarageService garageService;

    public ReferenceDataController(GarageService garageService) {
        this.garageService = garageService;
    }

    @GetMapping("/brands")
    public List<CarBrandDto> getBrands() {
        return garageService.listBrands();
    }

    @GetMapping("/brands/{brandId}/models")
    public List<CarModelDto> getModelsByBrand(@PathVariable UUID brandId) {
        return garageService.listModelsByBrand(brandId);
    }

    @GetMapping("/drivetrains")
    public List<CarDrivetrainDto> getDrivetrains() {
        return garageService.listDrivetrains();
    }

    @GetMapping("/colors")
    public List<CarColorDto> getColors() {
        return garageService.listColors();
    }

    @GetMapping("/distance-units")
    public List<CarDistanceUnitDto> getDistanceUnits() {
        return garageService.listDistanceUnits();
    }

    @GetMapping("/status-options")
    public List<CarStatusOptionDto> getStatusOptions() {
        return garageService.listStatusOptions();
    }

    @GetMapping("/mod-categories")
    public List<CarModCategoryDto> getModCategories() {
        return garageService.listModCategories();
    }
}
