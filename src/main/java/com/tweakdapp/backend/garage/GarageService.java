package com.tweakdapp.backend.garage;

import com.tweakdapp.backend.garage.dto.CarBrandDto;
import com.tweakdapp.backend.garage.dto.CarColorDto;
import com.tweakdapp.backend.garage.dto.CarDistanceUnitDto;
import com.tweakdapp.backend.garage.dto.CarFuelTypeOptionsDto;
import com.tweakdapp.backend.garage.dto.CarDrivetrainDto;
import com.tweakdapp.backend.garage.dto.CarDto;
import com.tweakdapp.backend.garage.dto.CarModCategoryDto;
import com.tweakdapp.backend.garage.dto.CarModelDto;
import com.tweakdapp.backend.garage.dto.DreamCarDto;
import com.tweakdapp.backend.garage.dto.request.DreamCarRequest;
import com.tweakdapp.backend.garage.dto.request.DreamCarRequestBody;
import com.tweakdapp.backend.garage.dto.response.AddModificationResponse;
import com.tweakdapp.backend.garage.dto.CarModificationDto;
import com.tweakdapp.backend.garage.dto.request.CarModificationRequest;
import com.tweakdapp.backend.garage.dto.request.CarRequest;
import com.tweakdapp.backend.garage.dto.request.UpdateModificationRequest;
import com.tweakdapp.backend.garage.dto.CarStatusOptionDto;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.garage.dto.request.CreateCarRequest;
import com.tweakdapp.backend.garage.dto.response.CreateCarResponse;
import com.tweakdapp.backend.garage.dto.GarageDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service interface for garage management, including cars, modifications, and reference data.
 *
 * A garage is a user's collection of cars. Cars belong to a garage and contain detailed
 * specifications and a list of modifications. This service handles all CRUD operations
 * on garages, cars, and modifications, enforcing ownership and privacy constraints.
 */
public interface GarageService {

    // ---- cross-module lookups ----------------------------------------------

    /**
     * Batch lookup of compact car summaries by ID, regardless of owning garage and
     * without privacy gating. Intended for other modules that reference cars by ID
     * (e.g. posts tagging a car) and need to render a badge without per-car calls.
     * Missing IDs are silently omitted; ordering is not guaranteed.
     *
     * @param ids the car IDs to resolve
     * @return the matching car summaries (may be smaller than {@code ids})
     */
    List<CarSummaryDto> findCarsByIds(Collection<UUID> ids);

    /**
     * Batch lookup of car brands by ID for other modules that reference a brand by id and need its
     * display name without per-row calls (e.g. the {@code forums} module rendering a thread's brand
     * badge). Missing IDs are silently omitted; ordering is not guaranteed.
     *
     * @param ids the brand IDs to resolve
     * @return the matching brands (could be smaller than {@code ids})
     */
    List<CarBrandDto> findBrandsByIds(Collection<UUID> ids);

    /**
     * Batch lookup of car models by ID for other modules that reference a model by id and need its
     * display name (and owning brand) without per-row calls (e.g. the {@code forums} module rendering
     * a thread's model badge). Missing IDs are silently omitted; ordering is not guaranteed.
     *
     * @param ids the model IDs to resolve
     * @return the matching models (could be smaller than {@code ids})
     */
    List<CarModelDto> findModelsByIds(Collection<UUID> ids);

    /**
     * The most-discussed brands (highest forum {@code thread_count} first, name as a tiebreaker),
     * excluding brands with no threads. Backs the forums "popular hubs" suggestions.
     *
     * @param limit the maximum number of brands to return
     */
    List<CarBrandDto> findTopBrandsByThreadCount(int limit);

    /**
     * The most-discussed models (highest forum {@code thread_count} first, model name as a
     * tiebreaker), excluding models with no threads. Backs the forums "popular hubs" suggestions.
     *
     * @param limit the maximum number of models to return
     */
    List<CarModelDto> findTopModelsByThreadCount(int limit);

    /**
     * Resolves each given car's owner (profile id), regardless of owning garage and without
     * privacy gating. Intended for other modules that must apply ownership-aware rules by id —
     * e.g. the posts module only allows a car to be tagged when its owner is also tagged.
     * Missing car ids are omitted from the result.
     *
     * @param carIds the car IDs to resolve
     * @return a map of car id → owner (profile) id (may be smaller than {@code carIds})
     */
    Map<UUID, UUID> findCarOwnerIds(Collection<UUID> carIds);

    /**
     * Every car id owned by the given profile, regardless of garage and without privacy gating.
     * The inverse of {@link #findCarOwnerIds}: the tags module needs a user's car ids to find the
     * content their cars were tagged in.
     *
     * @param ownerId the owning profile's id
     * @return the owner's car ids (empty if they have no cars)
     */
    List<UUID> findCarIdsByOwner(UUID ownerId);

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
     * Gets another user's garage by username. All accounts are public, so any user's garage is
     * visible to any authenticated viewer.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param username the username of the garage owner
     * @return the garage with a list of car summaries
     * @throws ProfileNotFoundException if the user does not exist
     * @throws GarageNotFoundException if the user has no garage (should be auto-created)
     */
    GarageDto getGarageByUsername(String currentUserId, String username);

    // ---- CARS --------------------------------------------------------------

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
     * Retrieves a single car with full details and all modifications. All accounts are public, so
     * any car is viewable by any authenticated user.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @return the car with all details and modifications
     * @throws CarNotFoundException if the car does not exist
     */
    CarDto getCar(String currentUserId, UUID carId);

    // ---- CAR MEDIA ------------------------------------------------

    /**
     * Saves the R2 key of the car's cover image. The key is issued by the StorageService
     * (storage module) when Flutter requests an upload URL; the public URL is built on read.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @param key the R2 object key of the uploaded cover image
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    void saveCarCoverImageKey(String currentUserId, UUID carId, String key);


    /**
     * Saves the R2 keys of a car's gallery images (full desired state, in order). Keys are
     * issued by the StorageService (storage module); public URLs are built on read.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @param imageKeys the R2 object keys of the gallery images, in display order
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    void saveGalleryImageKeys(String currentUserId, UUID carId, List<String> imageKeys);

    /**
     * Deletes the car's cover image: clears the {@code cover_image_url} column and removes the
     * underlying object from R2. The R2 deletion runs only after the DB transaction commits.
     * No-op if the car has no cover image.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    void deleteCarCoverImage(String currentUserId, UUID carId);

    /**
     * Deletes specific gallery images for a car by their keys: removes the matching gallery rows
     * and the underlying objects from R2. Only keys that actually belong to this car's gallery are
     * acted on; unknown keys are ignored. The R2 deletion runs only after the DB transaction commits.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @param keys the R2 object keys of the gallery images to delete
     * @throws CarNotFoundException if the car does not exist
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    void deleteGalleryImages(String currentUserId, UUID carId, List<String> keys);

    // ---- MODIFICATIONS -----------------------------------------------------

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
     * Partially updates a modification. Only non-null fields are applied; null means "no change".
     * Media can be added ({@code addMedia}) or removed ({@code removeMediaUrls}) in the same call.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @param modificationId the modification ID
     * @param request the partial update payload
     * @return the updated modification with current media
     * @throws CarModificationNotFoundException if the modification does not exist or
     *         does not belong to the specified car
     * @throws NotCarOwnerException if the current user is not the car owner
     * @throws InvalidReferenceException if the category ID is invalid
     */
    CarModificationDto patchModification(String currentUserId, UUID carId, UUID modificationId, UpdateModificationRequest request);

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

    /**
     * Deletes specific before/after media items of a modification by their keys: removes the matching
     * gallery rows and the underlying objects from R2. Only keys that actually belong to this
     * modification are acted on; unknown keys are ignored. The R2 deletion runs only after the DB
     * transaction commits.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param carId the car ID
     * @param modificationId the modification ID
     * @param keys the R2 object keys of the media items to delete
     * @throws CarModificationNotFoundException if the modification does not exist or
     *         does not belong to the specified car
     * @throws NotCarOwnerException if the current user is not the car owner
     */
    void deleteModificationMedia(String currentUserId, UUID carId, UUID modificationId, List<String> keys);

    // ---- REFERENCE DATA ----------------------------------------------------

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

    /**
     * Lists all available fuel type options (e.g., gasoline, diesel, hybrid, electric),
     * sorted alphabetically by name.
     *
     * @return list of fuel type options with IDs and names
     */
    List<CarFuelTypeOptionsDto> listFuelTypeOptions();

    // ---- DREAM CARS --------------------------------------------------------

    /**
     * Lists the current user's dream cars in display order.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @return the user's dream cars, ordered by position
     */
    List<DreamCarDto> listDreamCars(String currentUserId);

    /**
     * Adds one or more dream cars to the current user's list (appended at the end, in
     * request order).
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param request the dream cars to add, each with a brand (required) and optional model
     * @return the created dream cars, in request order
     * @throws InvalidReferenceException if a brand/model is unknown or the model
     *         does not belong to the brand
     */
    List<DreamCarDto> addDreamCar(String currentUserId, DreamCarRequest request);

    /**
     * Fully updates one of the current user's dream cars (brand, model).
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param dreamCarId the dream car ID
     * @param body the updated brand/model
     * @return the updated dream car
     * @throws DreamCarNotFoundException if it does not exist or is not owned by the user
     * @throws InvalidReferenceException if the brand/model is unknown or inconsistent
     */
    DreamCarDto updateDreamCar(String currentUserId, UUID dreamCarId, DreamCarRequestBody body);

    /**
     * Removes one of the current user's dream cars.
     *
     * @param currentUserId the current user's UUID from the JWT subject
     * @param dreamCarId the dream car ID
     * @throws DreamCarNotFoundException if it does not exist or is not owned by the user
     */
    void deleteDreamCar(String currentUserId, UUID dreamCarId);
}