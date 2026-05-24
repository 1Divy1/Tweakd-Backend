package com.carsocialmedia.backend.garage;

import com.carsocialmedia.backend.garage.dto.CarBrandDto;
import com.carsocialmedia.backend.garage.dto.CarColorDto;
import com.carsocialmedia.backend.garage.dto.CarDistanceUnitDto;
import com.carsocialmedia.backend.garage.dto.CarDrivetrainDto;
import com.carsocialmedia.backend.garage.dto.CarDto;
import com.carsocialmedia.backend.garage.dto.CarModCategoryDto;
import com.carsocialmedia.backend.garage.dto.CarModelDto;
import com.carsocialmedia.backend.garage.dto.AddModificationResponse;
import com.carsocialmedia.backend.garage.dto.CarModificationDto;
import com.carsocialmedia.backend.garage.dto.CarModificationRequest;
import com.carsocialmedia.backend.garage.dto.CarRequest;
import com.carsocialmedia.backend.garage.dto.CarStatusOptionDto;
import com.carsocialmedia.backend.garage.dto.CreateCarRequest;
import com.carsocialmedia.backend.garage.dto.CreateCarResponse;
import com.carsocialmedia.backend.garage.dto.GarageDto;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for garage management, including cars, modifications, and reference data.
 *
 * A garage is a user's collection of cars. Cars belong to a garage and contain detailed
 * specifications and a list of modifications. This service handles all CRUD operations
 * on garages, cars, and modifications, enforcing ownership and privacy constraints.
 */
public interface GarageService {

    // ---- garage views ------------------------------------------------------

    /**
     * Gets the current user's own garage (always visible, no privacy gating).
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @return the garage with a list of car summaries
     * @throws GarageNotFoundException if the user's garage does not exist (should be auto-created)
     */
    GarageDto getMyGarage(String currentUserId);

    /**
     * Gets another user's garage by username. Privacy rules mirror the profile / follow
     * social-graph: private profiles' garages are visible only to the owner or accepted followers.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param username the username of the garage owner
     * @return the garage with a list of car summaries
     * @throws ProfileNotFoundException if the user does not exist
     * @throws GarageNotFoundException if the user has no garage (should be auto-created)
     * @throws PrivateGarageException if the garage owner is private and the current user
     *         has not been accepted as a follower
     */
    GarageDto getGarageByUsername(String currentUserId, String username);

    // ---- cars --------------------------------------------------------------

    /**
     * Creates a car together with its modifications in one transaction.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param request the car and its modifications
     * @return the created car with all references resolved and modifications embedded
     * @throws GarageNotFoundException if the user's garage does not exist
     * @throws InvalidReferenceException if any referenced ID (brand, model, drivetrain, etc.)
     *         does not exist or is inconsistent (e.g., model doesn't belong to brand)
     */
    CreateCarResponse addCar(String currentUserId, CreateCarRequest request);

    /**
     * Updates a car owned by the current user (full replacement).
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @param request the updated car details
     * @return the updated car with all references resolved and current modifications
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     * @throws InvalidReferenceException if any referenced ID is invalid or inconsistent
     */
    CarDto updateCar(String currentUserId, UUID carId, CarRequest request);

    /**
     * Deletes a car owned by the current user. All modifications cascade and are deleted.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    void deleteCar(String currentUserId, UUID carId);

    /**
     * Retrieves a single car with full details and all modifications. If the viewer is
     * not the owner, visibility is subject to the owner's garage privacy settings.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @return the car with all details and modifications (prices may be hidden based on privacy)
     * @throws CarNotFoundException if the car does not exist
     * @throws PrivateGarageException if the garage is private and the viewer is not allowed
     */
    CarDto getCar(String currentUserId, UUID carId);

    // ---- modifications -----------------------------------------------------

    /**
     * Adds a new modification to a car owned by the current user.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @param request modification details (category ID, title, images, date, price, etc.)
     * @return the created modification
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     * @throws InvalidReferenceException if the modification category does not exist
     */
    AddModificationResponse addModification(String currentUserId, UUID carId, CarModificationRequest request);

    /**
     * Updates a modification on a car owned by the current user (full replacement).
     *
     * @param currentUserId the current user's UUID from the JWT subject
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
    CarModificationDto updateModification(String currentUserId, UUID carId, UUID modificationId, CarModificationRequest request);

    /**
     * Deletes a modification from a car owned by the current user.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @param modificationId the modification ID
     * @throws CarNotFoundException if the car does not exist
     * @throws CarModificationNotFoundException if the modification does not exist or
     *         does not belong to the specified car
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    void deleteModification(String currentUserId, UUID carId, UUID modificationId);

    // ---- reference data ----------------------------------------------------

    /**
     * Lists all available car brands, sorted alphabetically.
     *
     * @return list of car brands with IDs and names
     */
    List<CarBrandDto> listBrands();

    /**
     * Lists all car models for a specific brand, sorted alphabetically.
     *
     * @param brandId the brand ID
     * @return list of models for the brand with IDs and model names
     */
    List<CarModelDto> listModelsByBrand(UUID brandId);

    /**
     * Lists all available drivetrain types (e.g., FWD, RWD, AWD), sorted alphabetically.
     *
     * @return list of drivetrains with IDs and names
     */
    List<CarDrivetrainDto> listDrivetrains();

    /**
     * Lists all available paint/body colors with hex color codes, sorted alphabetically.
     *
     * @return list of colors with IDs, names, and hex codes
     */
    List<CarColorDto> listColors();

    /**
     * Lists all available distance units (e.g., kilometers, miles), sorted alphabetically.
     *
     * @return list of distance units with IDs and names
     */
    List<CarDistanceUnitDto> listDistanceUnits();

    /**
     * Lists all available car status options (e.g., daily, project car, collector),
     * sorted by type.
     *
     * @return list of status options with IDs and types
     */
    List<CarStatusOptionDto> listStatusOptions();

    /**
     * Lists all available modification categories (e.g., suspension, engine, wheels),
     * sorted alphabetically by modification name.
     *
     * @return list of modification categories with IDs and names
     */
    List<CarModCategoryDto> listModCategories();
}