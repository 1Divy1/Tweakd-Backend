package com.carsocialmedia.backend.garage;

import com.carsocialmedia.backend.garage.dto.CarBrandDto;
import com.carsocialmedia.backend.garage.dto.CarColorDto;
import com.carsocialmedia.backend.garage.dto.CarDistanceUnitDto;
import com.carsocialmedia.backend.garage.dto.CarDrivetrainDto;
import com.carsocialmedia.backend.garage.dto.CarDto;
import com.carsocialmedia.backend.garage.dto.CarModCategoryDto;
import com.carsocialmedia.backend.garage.dto.CarModelDto;
import com.carsocialmedia.backend.garage.dto.CarModificationDto;
import com.carsocialmedia.backend.garage.dto.CarModificationRequest;
import com.carsocialmedia.backend.garage.dto.CarRequest;
import com.carsocialmedia.backend.garage.dto.CarStatusOptionDto;
import com.carsocialmedia.backend.garage.dto.GarageDto;

import java.util.List;
import java.util.UUID;

public interface GarageService {

    // ---- garage views ------------------------------------------------------

    /** The current user's own garage (always visible, no privacy gating). */
    GarageDto getMyGarage(String currentUserId);

    /**
     * Another user's garage, addressed by username. Privacy rules mirror the profile /
     * follow social-graph rules: private profiles' garages are visible only to the owner
     * or accepted followers.
     */
    GarageDto getGarageByUsername(String currentUserId, String username);

    // ---- cars --------------------------------------------------------------

    /** Adds a car to the current user's garage. */
    CarDto addCar(String currentUserId, CarRequest request);

    /** Full update of a car owned by the current user. */
    CarDto updateCar(String currentUserId, UUID carId, CarRequest request);

    /** Deletes a car owned by the current user. Modifications cascade via the FK. */
    void deleteCar(String currentUserId, UUID carId);

    /** Single car detail (including modifications). Privacy-gated when viewer != owner. */
    CarDto getCar(String currentUserId, UUID carId);

    // ---- modifications -----------------------------------------------------

    CarModificationDto addModification(String currentUserId, UUID carId, CarModificationRequest request);

    CarModificationDto updateModification(String currentUserId, UUID carId, UUID modificationId, CarModificationRequest request);

    void deleteModification(String currentUserId, UUID carId, UUID modificationId);

    // ---- reference data ----------------------------------------------------

    List<CarBrandDto> listBrands();
    List<CarModelDto> listModelsByBrand(UUID brandId);
    List<CarDrivetrainDto> listDrivetrains();
    List<CarColorDto> listColors();
    List<CarDistanceUnitDto> listDistanceUnits();
    List<CarStatusOptionDto> listStatusOptions();
    List<CarModCategoryDto> listModCategories();
}
