package com.tweakdapp.backend.garage.internal.controllers;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarBrandDto;
import com.tweakdapp.backend.garage.dto.CarColorDto;
import com.tweakdapp.backend.garage.dto.CarDistanceUnitDto;
import com.tweakdapp.backend.garage.dto.CarFuelTypeOptionsDto;
import com.tweakdapp.backend.garage.dto.CarDrivetrainDto;
import com.tweakdapp.backend.garage.dto.CarModCategoryDto;
import com.tweakdapp.backend.garage.dto.CarModelDto;
import com.tweakdapp.backend.garage.dto.CarStatusOptionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for reference data (lookup tables) used in car and modification creation/editing.
 *
 * These endpoints provide the enumeration data needed by clients to populate dropdowns
 * and selectors when building car or modification records. All endpoints are read-only
 * and return cached reference data.
 */
@RestController
@RequestMapping("/api/v1/garage/reference")
public class ReferenceDataController {

    private final GarageService garageService;

    public ReferenceDataController(GarageService garageService) {
        this.garageService = garageService;
    }

    /**
     * Lists all car brands available in the system.
     *
     * @return list of brands sorted alphabetically by name
     */
    @GetMapping("/brands")
    public List<CarBrandDto> getBrands() {
        return garageService.listBrands();
    }

    /**
     * Lists all car models for a specific brand.
     *
     * @param brandId the brand ID
     * @return list of models for the brand, sorted alphabetically by model name
     */
    @GetMapping("/brands/{brandId}/models")
    public List<CarModelDto> getModelsByBrand(@PathVariable UUID brandId) {
        return garageService.listModelsByBrand(brandId);
    }

    /**
     * Lists all drivetrain types (e.g., FWD, RWD, AWD).
     *
     * @return list of drivetrains sorted alphabetically by name
     */
    @GetMapping("/drivetrains")
    public List<CarDrivetrainDto> getDrivetrains() {
        return garageService.listDrivetrains();
    }

    /**
     * Lists all paint/body colors.
     *
     * @return list of colors with names and hex codes, sorted alphabetically by name
     */
    @GetMapping("/colors")
    public List<CarColorDto> getColors() {
        return garageService.listColors();
    }

    /**
     * Lists all distance units (e.g., kilometers, miles).
     *
     * @return list of distance units sorted alphabetically by name
     */
    @GetMapping("/distance-units")
    public List<CarDistanceUnitDto> getDistanceUnits() {
        return garageService.listDistanceUnits();
    }

    /**
     * Lists all car status options (e.g., daily, project car, collector, etc.).
     *
     * @return list of status options sorted by type
     */
    @GetMapping("/status-options")
    public List<CarStatusOptionDto> getStatusOptions() {
        return garageService.listStatusOptions();
    }

    /**
     * Lists all modification categories (e.g., suspension, engine, wheels, etc.).
     *
     * @return list of categories sorted alphabetically by modification name
     */
    @GetMapping("/mod-categories")
    public List<CarModCategoryDto> getModCategories() {
        return garageService.listModCategories();
    }

    /**
     * Lists all fuel type options (e.g., gasoline, diesel, hybrid, electric).
     *
     * @return list of fuel type options sorted alphabetically by name
     */
    @GetMapping("/fuel-type-options")
    public List<CarFuelTypeOptionsDto> getFuelTypeOptions() {
        return garageService.listFuelTypeOptions();
    }
}
