package com.carsocialmedia.backend.garage;

import com.carsocialmedia.backend.garage.dto.CarBrandDto;
import com.carsocialmedia.backend.garage.dto.CarColorDto;
import com.carsocialmedia.backend.garage.dto.CarDistanceUnitDto;
import com.carsocialmedia.backend.garage.dto.CarDrivetrainDto;
import com.carsocialmedia.backend.garage.dto.CarDto;
import com.carsocialmedia.backend.garage.dto.CarImageDto;
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
import com.carsocialmedia.backend.garage.dto.ModificationUploadSlots;
import com.carsocialmedia.backend.garage.dto.UploadSlot;
import com.carsocialmedia.backend.garage.internal.entities.CarEntity;

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
     * Creates a car together with its modifications and gallery slots in one transaction,
     * then returns presigned upload URLs for every photo. The client uploads the bytes
     * directly to storage afterwards; on any upload failure it rolls back via
     * {@link #deleteCar(String, UUID)}.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param request the car, its modifications, and how many gallery slots to allocate
     * @return the created car plus cover / before-after / gallery upload slots
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
     * Non-owners viewing private garages are gated at the garage level.
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
     * Adds a new modification to a car owned by the current user. Storage paths for the
     * before/after images are generated and persisted; the response includes presigned upload
     * URLs the client should PUT the photo bytes to directly.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @param request modification details (category ID, title, images, date, price, etc.)
     * @return the created modification plus before/after presigned upload URLs
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

    // ---- upload URL refresh ------------------------------------------------

    /**
     * Returns a fresh presigned upload URL for the car's cover image. The storage path
     * is unchanged — the client PUTs the new bytes to overwrite the existing object.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car whose cover to re-upload
     * @return an upload slot with the existing path and a new presigned URL
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    UploadSlot refreshCoverUploadUrl(String currentUserId, UUID carId);

    /**
     * Returns fresh presigned upload URLs for a modification's before/after images.
     * The storage paths are unchanged — the client PUTs to overwrite the existing objects.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car the modification belongs to
     * @param modificationId the modification whose images to re-upload
     * @return upload slots for the before and after images
     * @throws CarNotFoundException if the car does not exist
     * @throws CarModificationNotFoundException if the modification does not exist or
     *         does not belong to the specified car
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    ModificationUploadSlots refreshModificationUploadUrls(String currentUserId, UUID carId, UUID modificationId);

    // ---- gallery images ----------------------------------------------------

    /**
     * Lists a car's gallery images ordered by display order. Subject to the same privacy
     * gate as {@link #getCar(String, UUID)}.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car whose gallery to list
     * @return the gallery image records (id, storage path, order, createdAt)
     * @throws CarNotFoundException if the car does not exist
     * @throws PrivateGarageException if the garage is private and the viewer is not allowed
     */
    List<CarImageDto> listCarImages(String currentUserId, UUID carId);

    /**
     * Deletes one gallery image record. Only the car owner may delete.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car the image belongs to
     * @param imageId the gallery image to delete
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     * @throws CarImageNotFoundException if the image does not exist or belongs to another car
     */
    void deleteCarImage(String currentUserId, UUID carId, UUID imageId);

    /**
     * Returns a short-lived presigned download URL for a car photo, after verifying the
     * caller is allowed to view that car (owner, or permitted by the privacy gate).
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param storagePath the canonical {@code car-photos/{ownerId}/{carId}/...} object path
     * @return a presigned download URL
     * @throws InvalidStoragePathException if the path is malformed
     * @throws CarNotFoundException if the path's car does not exist
     * @throws PrivateGarageException if the garage is private and the viewer is not allowed
     */
    String generateCarImageDownloadUrl(String currentUserId, String storagePath);
}
