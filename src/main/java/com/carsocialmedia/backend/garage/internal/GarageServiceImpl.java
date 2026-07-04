package com.carsocialmedia.backend.garage.internal;

import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.CarBrandDto;
import com.carsocialmedia.backend.garage.dto.CarColorDto;
import com.carsocialmedia.backend.garage.dto.CarDistanceUnitDto;
import com.carsocialmedia.backend.garage.dto.CarFuelTypeOptionsDto;
import com.carsocialmedia.backend.garage.dto.CarDrivetrainDto;
import com.carsocialmedia.backend.garage.dto.CarDto;
import com.carsocialmedia.backend.garage.dto.CarModCategoryDto;
import com.carsocialmedia.backend.garage.dto.CarModelDto;
import com.carsocialmedia.backend.garage.dto.CarOwnerDto;
import com.carsocialmedia.backend.garage.dto.response.AddModificationResponse;
import com.carsocialmedia.backend.garage.dto.CarModificationDto;
import com.carsocialmedia.backend.garage.dto.CarModificationMediaDto;
import com.carsocialmedia.backend.garage.dto.request.CarModificationRequest;
import com.carsocialmedia.backend.garage.dto.request.CarRequest;
import com.carsocialmedia.backend.garage.dto.request.UpdateModificationRequest;
import com.carsocialmedia.backend.garage.dto.CarStatusOptionDto;
import com.carsocialmedia.backend.garage.dto.MediaRefDto;
import com.carsocialmedia.backend.garage.dto.CarSummaryDto;
import com.carsocialmedia.backend.garage.dto.DreamCarDto;
import com.carsocialmedia.backend.garage.dto.request.CreateCarRequest;
import com.carsocialmedia.backend.garage.dto.request.DreamCarRequest;
import com.carsocialmedia.backend.garage.dto.request.DreamCarRequestBody;
import com.carsocialmedia.backend.garage.dto.response.CreateCarResponse;
import com.carsocialmedia.backend.garage.dto.GarageDto;
import com.carsocialmedia.backend.garage.exception.CarModificationNotFoundException;
import com.carsocialmedia.backend.garage.exception.CarNotFoundException;
import com.carsocialmedia.backend.garage.exception.DreamCarNotFoundException;
import com.carsocialmedia.backend.garage.exception.GarageNotFoundException;
import com.carsocialmedia.backend.garage.exception.InvalidReferenceException;
import com.carsocialmedia.backend.garage.exception.NotCarOwnerException;
import com.carsocialmedia.backend.garage.internal.entities.*;
import com.carsocialmedia.backend.garage.internal.repositories.*;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.exception.ProfileNotFoundException;
import com.carsocialmedia.backend.storage.StorageBucket;
import com.carsocialmedia.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class GarageServiceImpl implements GarageService {

    private static final Logger log = LoggerFactory.getLogger(GarageServiceImpl.class);

    private final GarageRepository garageRepository;
    private final CarRepository carRepository;
    private final CarModificationRepository modificationRepository;
    private final CarModificationGalleryRepository modificationGalleryRepository;
    private final CarGalleryRepository carGalleryRepository;
    private final CarBrandRepository brandRepository;
    private final CarModelRepository modelRepository;
    private final CarDrivetrainRepository drivetrainRepository;
    private final CarColorRepository colorRepository;
    private final CarDistanceUnitRepository distanceUnitRepository;
    private final CarFuelTypeOptionsRepository fuelTypeOptionsRepository;
    private final CarStatusOptionRepository statusOptionRepository;
    private final CarModCategoryRepository modCategoryRepository;
    private final DreamCarRepository dreamCarRepository;
    private final ProfileService profileService;
    private final StorageService storageService;

    // Question: what does it do and why do we need it ?
    @PersistenceContext
    private EntityManager entityManager;

    GarageServiceImpl(GarageRepository garageRepository,
                      CarRepository carRepository,
                      CarModificationRepository modificationRepository,
                      CarModificationGalleryRepository modificationGalleryRepository,
                      CarGalleryRepository carGalleryRepository,
                      CarBrandRepository brandRepository,
                      CarModelRepository modelRepository,
                      CarDrivetrainRepository drivetrainRepository,
                      CarColorRepository colorRepository,
                      CarDistanceUnitRepository distanceUnitRepository,
                      CarFuelTypeOptionsRepository fuelTypeOptionsRepository,
                      CarStatusOptionRepository statusOptionRepository,
                      CarModCategoryRepository modCategoryRepository,
                      DreamCarRepository dreamCarRepository,
                      ProfileService profileService,
                      StorageService storageService) {
        this.garageRepository = garageRepository;
        this.carRepository = carRepository;
        this.modificationRepository = modificationRepository;
        this.modificationGalleryRepository = modificationGalleryRepository;
        this.carGalleryRepository = carGalleryRepository;
        this.brandRepository = brandRepository;
        this.modelRepository = modelRepository;
        this.drivetrainRepository = drivetrainRepository;
        this.colorRepository = colorRepository;
        this.distanceUnitRepository = distanceUnitRepository;
        this.fuelTypeOptionsRepository = fuelTypeOptionsRepository;
        this.statusOptionRepository = statusOptionRepository;
        this.modCategoryRepository = modCategoryRepository;
        this.dreamCarRepository = dreamCarRepository;
        this.profileService = profileService;
        this.storageService = storageService;
    }

    // -------------------------------------------------------------------
    // GARAGE VIEWS
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public GarageDto getMyGarage(String currentUserId) {
        UUID userId = UUID.fromString(currentUserId);
        GarageEntity garage = garageRepository.findByOwnerId(userId)
                .orElseThrow(() -> GarageNotFoundException.forOwner(currentUserId));
        return toGarageDto(garage);
    }

    @Override
    @Transactional(readOnly = true)
    public GarageDto getGarageByUsername(String currentUserId, String username) {

        // Check if the user exists
        UUID ownerId = profileService
                .findIdByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));

        // Fetch the garage entity for the owner if it exists or throw if not found
        GarageEntity garage = garageRepository
                .findByOwnerId(ownerId)
                .orElseThrow(() -> GarageNotFoundException.forOwner(ownerId.toString()));

        return toGarageDto(garage);
    }

    // -------------------------------------------------------------------
    // BASIC CAR CRUD
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public CreateCarResponse addCar(String currentUserId, CreateCarRequest request) {
        UUID userId = UUID.fromString(currentUserId);
        GarageEntity garage = garageRepository.findByOwnerId(userId)
                .orElseThrow(() -> GarageNotFoundException.forOwner(currentUserId));

        UUID carId = UUID.randomUUID();
        CarEntity car = new CarEntity();
        car.setId(carId);
        car.setGarage(garage);
        applyCarRequest(car, request.car());
        carRepository.save(car);

        for (CarModificationRequest modReq : request.modifications()) {
            UUID modId = UUID.randomUUID();
            CarModificationEntity mod = new CarModificationEntity();
            mod.setId(modId);
            mod.setCar(car);
            applyModificationRequest(mod, modReq);
            modificationRepository.save(mod);
        }

        // Flush the INSERTs and clear the persistence context so the detail
        // queries below execute real SELECTs and load DB-managed columns
        // (e.g. createdAt) instead of returning the cached, partially-populated
        // instances.
        entityManager.flush();
        entityManager.clear();

        // Refresh through detail query so collections / lazy refs are fetched within tx.
        CarEntity hydrated = carRepository.findDetailById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        List<CarModificationEntity> mods = modificationRepository.findByCarIdWithCategory(carId);
        CarDto carDto = toCarDto(hydrated, mods);

        return new CreateCarResponse(carDto);
    }

    @Override
    @Transactional
    public CarDto updateCar(String currentUserId, UUID carId, CarRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        CarEntity car = carRepository.findDetailById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        ensureOwnership(car, userId);

        applyCarRequest(car, request);
        carRepository.save(car);

        List<CarModificationEntity> mods = modificationRepository.findByCarIdWithCategory(carId);
        return toCarDto(car, mods);
    }

    @Override
    @Transactional
    public void deleteCar(String currentUserId, UUID carId) {
        UUID userId = UUID.fromString(currentUserId);

        CarEntity car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        ensureOwnership(car, userId);

        // Collect every R2 object owned by this car BEFORE the DB rows disappear: the cover image,
        // all gallery images, and all media of every modification. The DB child rows are removed by
        // the Supabase ON DELETE CASCADE, but that cascade only touches the database, not R2.
        List<String> r2Urls = new ArrayList<>();
        if (car.getCoverImageKey() != null) {
            r2Urls.add(car.getCoverImageKey());
        }
        carGalleryRepository.findAllByCarIdOrderByPositionAsc(carId).stream()
                .map(CarGalleryEntity::getKey)
                .forEach(r2Urls::add);
        modificationGalleryRepository.findAllByCarId(carId).stream()
                .map(CarModificationGalleryEntity::getKey)
                .forEach(r2Urls::add);

        // car_modifications.car_id is ON DELETE CASCADE in Supabase.
        carRepository.delete(car);

        deleteR2ObjectsAfterCommit(r2Urls, carId);
    }

    @Override
    @Transactional(readOnly = true)
    public CarDto getCar(String currentUserId, UUID carId) {
        CarEntity car = carRepository
                .findDetailById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));

        List<CarModificationEntity> mods = modificationRepository.findByCarIdWithCategory(carId);
        return toCarDto(car, mods);
    }

    // -------------------------------------------------------------------
    // CAR MEDIA
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public void saveCarCoverImageKey(String currentUserId, UUID carId, String key) {

        // Extract the user's ID from the request
        UUID userId = UUID.fromString(currentUserId);

        // Find the car in DB by its ID
        CarEntity car = carRepository
                .findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));

        // Verify that the current user is the owner of the car
        ensureOwnership(car, userId);

        // Persist the R2 key (not the full URL) and save the changes in the DB
        car.setCoverImageKey(key);
        carRepository.save(car);
    }

    @Override
    @Transactional
    public void saveGalleryImageKeys(String currentUserId, UUID carId, List<String> imageKeys) {
        UUID userId = UUID.fromString(currentUserId);

        CarEntity car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        ensureOwnership(car, userId);

        // Diff: find keys that are in the DB but not in the incoming list — those are removed.
        Set<String> incomingSet = new HashSet<>(imageKeys);
        List<String> removedKeys = carGalleryRepository.findAllByCarIdOrderByPositionAsc(carId)
                .stream()
                .map(CarGalleryEntity::getKey)
                .filter(key -> !incomingSet.contains(key))
                .toList();

        // DB: replace-all to persist the final ordered state.
        carGalleryRepository.deleteAllByCarId(carId);
        List<CarGalleryEntity> entries = new ArrayList<>();
        for (int i = 0; i < imageKeys.size(); i++) {
            CarGalleryEntity entry = new CarGalleryEntity();
            entry.setId(UUID.randomUUID());
            entry.setCar(car);
            entry.setKey(imageKeys.get(i));
            entry.setPosition(i);
            entries.add(entry);
        }
        carGalleryRepository.saveAll(entries);

        // R2: delete removed objects only after the DB transaction commits.
        deleteR2ObjectsAfterCommit(removedKeys, carId);
    }

    @Override
    @Transactional
    public void deleteCarCoverImage(String currentUserId, UUID carId) {
        UUID userId = UUID.fromString(currentUserId);

        CarEntity car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        ensureOwnership(car, userId);

        String coverKey = car.getCoverImageKey();
        if (coverKey == null) {
            return;
        }

        car.setCoverImageKey(null);
        carRepository.save(car);

        deleteR2ObjectsAfterCommit(List.of(coverKey), carId);
    }

    @Override
    @Transactional
    public void deleteGalleryImages(String currentUserId, UUID carId, List<String> keys) {
        UUID userId = UUID.fromString(currentUserId);

        CarEntity car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        ensureOwnership(car, userId);

        if (keys == null || keys.isEmpty()) {
            return;
        }

        // Only act on keys that actually belong to this car's gallery. This keeps the DB scope and
        // the R2 deletion in sync and prevents an owner from deleting unrelated R2 objects by key.
        Set<String> requested = new HashSet<>(keys);
        List<String> toDelete = carGalleryRepository.findAllByCarIdOrderByPositionAsc(carId).stream()
                .map(CarGalleryEntity::getKey)
                .filter(requested::contains)
                .toList();
        if (toDelete.isEmpty()) {
            return;
        }

        carGalleryRepository.deleteAllByCarIdAndKeyIn(carId, toDelete);
        deleteR2ObjectsAfterCommit(toDelete, carId);
    }

    // -------------------------------------------------------------------
    // MODIFICATIONS
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public AddModificationResponse addModification(String currentUserId, UUID carId, CarModificationRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        CarEntity car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        ensureOwnership(car, userId);

        UUID modId = UUID.randomUUID();
        CarModificationEntity mod = new CarModificationEntity();
        mod.setId(modId);
        mod.setCar(car);
        applyModificationRequest(mod, request);

        modificationRepository.save(mod);
        // Flush the INSERT then clear the context so the re-fetch below issues a
        // real SELECT and picks up DB-managed columns (e.g. createdAt).
        entityManager.flush();
        entityManager.clear();
        CarModificationEntity reloaded = modificationRepository.findById(modId)
                .orElseThrow(() -> new CarModificationNotFoundException(modId));

        // New mod has no media yet — images are uploaded separately after creation.
        return new AddModificationResponse(toModificationDto(reloaded, List.of()));
    }

    @Override
    @Transactional
    public CarModificationDto patchModification(String currentUserId, UUID carId, UUID modificationId, UpdateModificationRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        CarModificationEntity mod = modificationRepository.findById(modificationId)
                .orElseThrow(() -> new CarModificationNotFoundException(modificationId));

        if (!mod.getCar().getId().equals(carId)) {
            throw new CarModificationNotFoundException(modificationId);
        }
        ensureOwnership(mod.getCar(), userId);

        // Apply only the non-null text fields.
        if (request.categoryId() != null) {
            CarModCategoryEntity category = modCategoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new InvalidReferenceException("Unknown modification category id: " + request.categoryId()));
            mod.setCategory(category);
        }
        if (request.title() != null)            mod.setTitle(request.title());
        if (request.description() != null)      mod.setDescription(request.description());
        if (request.installationDate() != null) mod.setInstallationDate(request.installationDate());
        if (request.price() != null)            mod.setPrice(request.price());
        if (request.mileageAtInstall() != null) mod.setMileageAtInstall(request.mileageAtInstall());

        modificationRepository.save(mod);

        // Remove media items first so a re-upload of the same key isn't double-inserted.
        // Scope to keys that actually belong to this mod, then delete the rows and the R2 objects.
        if (request.removeMediaKeys() != null && !request.removeMediaKeys().isEmpty()) {
            Set<String> requested = new HashSet<>(request.removeMediaKeys());
            List<String> toDelete = modificationGalleryRepository.findAllByModification_Id(modificationId).stream()
                    .map(CarModificationGalleryEntity::getKey)
                    .filter(requested::contains)
                    .toList();
            if (!toDelete.isEmpty()) {
                modificationGalleryRepository.deleteAllByModification_IdAndKeyIn(modificationId, toDelete);
                deleteR2ObjectsAfterCommit(toDelete, carId);
            }
        }

        if (request.addMedia() != null) {
            for (UpdateModificationRequest.MediaItem item : request.addMedia()) {
                insertModificationMedia(mod, item.key(), item.phase());
            }
        }

        List<CarModificationGalleryEntity> media = modificationGalleryRepository.findAllByModification_Id(modificationId);
        return toModificationDto(mod, media);
    }

    @Override
    @Transactional
    public void deleteModification(String currentUserId, UUID carId, UUID modificationId) {
        UUID userId = UUID.fromString(currentUserId);

        CarModificationEntity mod = modificationRepository.findById(modificationId)
                .orElseThrow(() -> new CarModificationNotFoundException(modificationId));

        if (!mod.getCar().getId().equals(carId)) {
            throw new CarModificationNotFoundException(modificationId);
        }
        ensureOwnership(mod.getCar(), userId);

        // Collect this mod's media keys before deletion. Gallery rows go via the Supabase
        // ON DELETE CASCADE, but the R2 objects must be removed explicitly.
        List<String> r2Keys = modificationGalleryRepository.findAllByModification_Id(modificationId).stream()
                .map(CarModificationGalleryEntity::getKey)
                .toList();

        modificationRepository.delete(mod);

        deleteR2ObjectsAfterCommit(r2Keys, carId);
    }

    @Override
    @Transactional
    public void deleteModificationMedia(String currentUserId, UUID carId, UUID modificationId, List<String> keys) {
        UUID userId = UUID.fromString(currentUserId);

        CarModificationEntity mod = modificationRepository.findById(modificationId)
                .orElseThrow(() -> new CarModificationNotFoundException(modificationId));

        if (!mod.getCar().getId().equals(carId)) {
            throw new CarModificationNotFoundException(modificationId);
        }
        ensureOwnership(mod.getCar(), userId);

        if (keys == null || keys.isEmpty()) {
            return;
        }

        // Only act on keys that actually belong to this modification — keeps the DB scope and the
        // R2 deletion in sync and prevents deleting unrelated R2 objects by key.
        Set<String> requested = new HashSet<>(keys);
        List<String> toDelete = modificationGalleryRepository.findAllByModification_Id(modificationId).stream()
                .map(CarModificationGalleryEntity::getKey)
                .filter(requested::contains)
                .toList();
        if (toDelete.isEmpty()) {
            return;
        }

        modificationGalleryRepository.deleteAllByModification_IdAndKeyIn(modificationId, toDelete);
        deleteR2ObjectsAfterCommit(toDelete, carId);
    }

    // -------------------------------------------------------------------
    // REFERENCE DATA
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CarBrandDto> listBrands() {
        return brandRepository.findAllByOrderByNameAsc().stream()
                .map(b -> new CarBrandDto(b.getId(), b.getName(), b.getThreadCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarModelDto> listModelsByBrand(UUID brandId) {
        return modelRepository.findByBrandIdOrderByModelAsc(brandId).stream()
                .map(m -> new CarModelDto(m.getId(), m.getBrand().getId(), m.getModel(), m.getThreadCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarDrivetrainDto> listDrivetrains() {
        return drivetrainRepository.findAllByOrderByNameAsc().stream()
                .map(d -> new CarDrivetrainDto(d.getId(), d.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarColorDto> listColors() {
        return colorRepository.findAllByOrderByNameAsc().stream()
                .map(c -> new CarColorDto(c.getId(), c.getName(), c.getColorCode()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarDistanceUnitDto> listDistanceUnits() {
        return distanceUnitRepository.findAllByOrderByNameAsc().stream()
                .map(u -> new CarDistanceUnitDto(u.getId(), u.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarStatusOptionDto> listStatusOptions() {
        return statusOptionRepository.findAllByOrderByTypeAsc().stream()
                .map(s -> new CarStatusOptionDto(s.getId(), s.getType()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarModCategoryDto> listModCategories() {
        return modCategoryRepository.findAllByOrderByModNameAsc().stream()
                .map(c -> new CarModCategoryDto(c.getId(), c.getModName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarFuelTypeOptionsDto> listFuelTypeOptions() {
        return fuelTypeOptionsRepository.findAllByOrderByNameAsc().stream()
                .map(f -> new CarFuelTypeOptionsDto(f.getId(), f.getName()))
                .toList();
    }

    // -------------------------------------------------------------------
    // DREAM CARS
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<DreamCarDto> listDreamCars(String currentUserId) {
        UUID userId = UUID.fromString(currentUserId);
        return dreamCarRepository.findByProfileIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toDreamCarDto)
                .toList();
    }

    @Override
    @Transactional
    public List<DreamCarDto> addDreamCar(String currentUserId, DreamCarRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        List<UUID> createdIds = new ArrayList<>();
        for (DreamCarRequestBody body : request.dreamCars()) {
            DreamCarEntity dreamCar = new DreamCarEntity();
            dreamCar.setId(UUID.randomUUID());
            dreamCar.setProfileId(userId);
            applyDreamCarBody(dreamCar, body);
            dreamCarRepository.save(dreamCar);
            createdIds.add(dreamCar.getId());
        }

        // Flush + clear so the re-fetch picks up the DB-managed createdAt.
        entityManager.flush();
        entityManager.clear();
        return createdIds.stream()
                .map(id -> dreamCarRepository.findById(id)
                        .orElseThrow(() -> new DreamCarNotFoundException(id)))
                .map(this::toDreamCarDto)
                .toList();
    }

    @Override
    @Transactional
    public DreamCarDto updateDreamCar(String currentUserId, UUID dreamCarId, DreamCarRequestBody body) {
        UUID userId = UUID.fromString(currentUserId);

        DreamCarEntity dreamCar = dreamCarRepository.findByIdAndProfileId(dreamCarId, userId)
                .orElseThrow(() -> new DreamCarNotFoundException(dreamCarId));
        applyDreamCarBody(dreamCar, body);
        dreamCarRepository.save(dreamCar);
        return toDreamCarDto(dreamCar);
    }

    @Override
    @Transactional
    public void deleteDreamCar(String currentUserId, UUID dreamCarId) {
        UUID userId = UUID.fromString(currentUserId);

        DreamCarEntity dreamCar = dreamCarRepository.findByIdAndProfileId(dreamCarId, userId)
                .orElseThrow(() -> new DreamCarNotFoundException(dreamCarId));
        dreamCarRepository.delete(dreamCar);
    }

    /**
     * Resolves and validates the brand/model references of a single dream car body. The
     * model is optional; when present it must belong to the chosen brand.
     *
     * @param dreamCar the entity to populate (mutated in-place)
     * @param body the dream car body
     * @throws InvalidReferenceException if the brand/model is unknown or inconsistent
     */
    private void applyDreamCarBody(DreamCarEntity dreamCar, DreamCarRequestBody body) {
        CarBrandEntity brand = brandRepository.findById(body.brandId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown brand id: " + body.brandId()));

        CarModelEntity model = null;
        if (body.modelId() != null) {
            model = modelRepository.findById(body.modelId())
                    .orElseThrow(() -> new InvalidReferenceException("Unknown model id: " + body.modelId()));
            if (!model.getBrand().getId().equals(brand.getId())) {
                throw new InvalidReferenceException("Model " + body.modelId() + " does not belong to brand " + body.brandId());
            }
        }

        dreamCar.setBrand(brand);
        dreamCar.setModel(model);
    }

    private DreamCarDto toDreamCarDto(DreamCarEntity d) {
        CarModelEntity model = d.getModel();
        return new DreamCarDto(
                d.getId(),
                d.getBrand().getId(),
                d.getBrand().getName(),
                model == null ? null : model.getId(),
                model == null ? null : model.getModel(),
                d.getCreatedAt());
    }

    // -------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------

    /**
     * Registers an after-commit callback that deletes the given objects from R2.
     *
     * Deletion runs only if the surrounding DB transaction commits — if it rolls back, the
     * callback never fires and R2 is untouched. If R2 deletion fails after commit, the DB is
     * already consistent and we just log: those files become orphans but nothing references them.
     *
     * @param keys R2 object keys to delete (empty list is a no-op)
     * @param carId the owning car ID, for log context
     */
    private void deleteR2ObjectsAfterCommit(List<String> keys, UUID carId) {
        if (keys.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.deleteByKeys(StorageBucket.GARAGE, keys);
                } catch (Exception e) {
                    log.warn("DB committed but failed to delete {} orphaned R2 objects for car {}",
                            keys.size(), carId, e);
                }
            }
        });
    }

    /**
     * Applies a car request (create or update) to a car entity, resolving all reference
     * IDs and validating consistency constraints.
     *
     * Validates that:
     * <ul>
     *   <li>Brand exists</li>
     *   <li>Model exists and belongs to the selected brand</li>
     *   <li>Drivetrain, color, and mileage unit exist</li>
     * </ul>
     *
     * @param car the entity to populate (mutated in-place)
     * @param req the request payload with validated specs and reference IDs
     * @throws InvalidReferenceException if any lookup table row doesn't exist or is inconsistent
     */
    private void applyCarRequest(CarEntity car, CarRequest req) {

        CarBrandEntity brand = brandRepository
                .findById(req.brandId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown brand id: " + req.brandId()));
        CarModelEntity model = modelRepository
                .findById(req.modelId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown model id: " + req.modelId()));

        // Reject inconsistent brand/model pairs — the DB lets this through but it'd produce
        // a nonsensical "BMW M3 with Honda brand" if the client mis-wired the dropdown.
        if (!model.getBrand().getId().equals(brand.getId())) {
            throw new InvalidReferenceException("Model " + req.modelId() + " does not belong to brand " + req.brandId());
        }
        CarDrivetrainEntity drivetrain = drivetrainRepository
                .findById(req.drivetrainId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown drivetrain id: " + req.drivetrainId()));
        CarColorEntity color = colorRepository
                .findById(req.colorId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown color id: " + req.colorId()));
        CarDistanceUnitEntity unit = distanceUnitRepository
                .findById(req.mileageUnitId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown mileage unit id: " + req.mileageUnitId()));
        CarStatusOptionEntity status = statusOptionRepository
                .findById(req.statusId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown status id: " + req.statusId()));
        CarFuelTypeOptionsEntity fuelType = fuelTypeOptionsRepository
                .findById(req.fuelTypeId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown fuel type id: " + req.fuelTypeId()));

        car.setBrand(brand);
        car.setModel(model);
        car.setDrivetrain(drivetrain);
        car.setColor(color);
        car.setMileageUnit(unit);
        car.setMileage(req.mileage());
        car.setStatus(status);
        car.setYear(req.year());
        car.setHorsepower(req.horsepower());
        car.setTorque(req.torque());
        car.setWeight(req.weight());
        car.setEngineDisplacement(req.engineDisplacement());
        car.setZeroToOneHundred(req.zeroToOneHundred());
        car.setChassisCode(req.chassisCode());
        car.setModelCode(req.modelCode());
        car.setEngineCode(req.engineCode());
        car.setStory(req.story());
        car.setFuelType(fuelType);
    }

    /**
     * Applies a modification request (create or update) to a modification entity,
     * resolving the category reference and copying all fields.
     *
     * @param mod the entity to populate (mutated in-place)
     * @param req the request payload with validated details
     * @throws InvalidReferenceException if the modification category doesn't exist
     */
    private void applyModificationRequest(CarModificationEntity mod, CarModificationRequest req) {

        CarModCategoryEntity category = modCategoryRepository.findById(req.categoryId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown modification category id: " + req.categoryId()));

        mod.setCategory(category);
        mod.setTitle(req.title());
        mod.setDescription(req.description());
        mod.setInstallationDate(req.installationDate());
        mod.setPrice(req.price());
        mod.setMileageAtInstall(req.mileageAtInstall());
    }

    /**
     * Verifies that the given user is the owner of the car.
     *
     * Used for all car mutations (update, delete, add/update/delete modifications).
     * Only the car owner can modify a car.
     *
     * @param car the car entity
     * @param userId the user ID to check
     * @throws NotCarOwnerException if the user is not the car owner
     */
    private void ensureOwnership(CarEntity car, UUID userId) {
        if (!car.getGarage().getOwnerId().equals(userId)) {
            throw new NotCarOwnerException();
        }
    }

    /**
     * Converts a garage entity to a DTO with a list of car summaries.
     *
     * The car list is fetched with a specialized query ({@link CarRepository#findGarageSummary})
     * that eagerly loads brand and model to avoid N+1 queries when building summaries.
     *
     * @param garage the garage entity
     * @return the garage DTO with car summaries
     */
    @Override
    @Transactional(readOnly = true)
    public List<CarSummaryDto> findCarsByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return toCarSummaries(carRepository.findSummaryByIds(ids));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarBrandDto> findBrandsByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return brandRepository.findAllById(ids).stream()
                .map(b -> new CarBrandDto(b.getId(), b.getName(), b.getThreadCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarModelDto> findModelsByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return modelRepository.findAllById(ids).stream()
                .map(m -> new CarModelDto(m.getId(), m.getBrand().getId(), m.getModel(), m.getThreadCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarBrandDto> findTopBrandsByThreadCount(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return brandRepository.findByThreadCountGreaterThanOrderByThreadCountDescNameAsc(0, PageRequest.of(0, limit)).stream()
                .map(b -> new CarBrandDto(b.getId(), b.getName(), b.getThreadCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarModelDto> findTopModelsByThreadCount(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return modelRepository.findByThreadCountGreaterThanOrderByThreadCountDescModelAsc(0, PageRequest.of(0, limit)).stream()
                .map(m -> new CarModelDto(m.getId(), m.getBrand().getId(), m.getModel(), m.getThreadCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UUID> findCarOwnerIds(Collection<UUID> carIds) {
        if (carIds == null || carIds.isEmpty()) {
            return Map.of();
        }
        return carRepository.findOwnerIdsByCarIds(carIds).stream()
                .collect(Collectors.toMap(CarRepository.CarOwner::getCarId, CarRepository.CarOwner::getOwnerId));
    }

    private GarageDto toGarageDto(GarageEntity garage) {
        List<CarSummaryDto> cars = toCarSummaries(carRepository.findGarageSummary(garage.getId()));
        return new GarageDto(
                garage.getId(),
                garage.getOwnerId(),
                garage.getCreatedAt(),
                cars);
    }

    /**
     * Converts a car entity to a DTO with all specifications and modifications.
     *
     * Denormalizes reference values (e.g., brandId + brandName) so the client has both
     * the ID (for form state) and display label (for rendering) without needing separate
     * lookups.
     *
     * @param car the car entity (with all references eagerly loaded)
     * @param mods the list of modifications for this car
     * @return the car DTO with all specifications and modifications
     */
    private CarDto toCarDto(CarEntity car, List<CarModificationEntity> mods) {

        // Load all media for this car's modifications in one query, then group by mod ID.
        Map<UUID, List<CarModificationGalleryEntity>> mediaByModId = modificationGalleryRepository
                .findAllByCarId(car.getId()).stream()
                .collect(Collectors.groupingBy(g -> g.getModification().getId()));

        List<CarModificationDto> modDtos = mods
                .stream()
                .map(m -> toModificationDto(m, mediaByModId.getOrDefault(m.getId(), List.of())))
                .toList();

        List<MediaRefDto> gallery = carGalleryRepository.findAllByCarIdOrderByPositionAsc(car.getId()).stream()
                .map(CarGalleryEntity::getKey)
                .map(this::toMediaRef)
                .toList();

        return new CarDto(
                car.getId(),
                car.getGarage().getId(),
                car.getBrand().getId(),
                car.getBrand().getName(),
                car.getModel().getId(),
                car.getModel().getModel(),
                car.getDrivetrain().getId(),
                car.getDrivetrain().getName(),
                car.getColor().getId(),
                car.getColor().getName(),
                car.getColor().getColorCode(),
                car.getMileageUnit().getId(),
                car.getMileageUnit().getName(),
                car.getMileage(),
                car.getYear(),
                car.getHorsepower(),
                car.getTorque(),
                car.getWeight(),
                car.getEngineDisplacement(),
                car.getZeroToOneHundred(),
                car.getChassisCode(),
                car.getModelCode(),
                car.getEngineCode(),
                toMediaRef(car.getCoverImageKey()),
                gallery,
                car.getCreatedAt(),
                car.getFuelType().getId(),
                car.getFuelType().getName(),
                car.getStatus().getId(),
                car.getStatus().getType(),
                car.getStory(),
                modDtos);
    }

    /**
     * Maps cars to summaries, batch-resolving each car owner's username in a single
     * profile lookup to avoid an N+1 across the list.
     */
    private List<CarSummaryDto> toCarSummaries(List<CarEntity> cars) {
        Set<UUID> ownerIds = cars.stream()
                .map(c -> c.getGarage().getOwnerId())
                .collect(Collectors.toSet());
        Map<UUID, String> usernamesById = profileService.findByIds(ownerIds).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, ProfileSearchResultDto::username));
        return cars.stream()
                .map(c -> toCarSummaryDto(c, usernamesById))
                .toList();
    }

    private CarSummaryDto toCarSummaryDto(CarEntity c, Map<UUID, String> usernamesById) {
        UUID ownerId = c.getGarage().getOwnerId();
        CarOwnerDto owner = new CarOwnerDto(ownerId, usernamesById.get(ownerId));
        return new CarSummaryDto(
                c.getId(),
                c.getBrand().getName(),
                c.getModel().getModel(),
                toMediaRef(c.getCoverImageKey()),
                toStatusOptionDto(c.getStatus()),
                owner);
    }

    /** Builds a {key, url} pair for a stored R2 object. Returns {@code null} if the key is null/blank. */
    private MediaRefDto toMediaRef(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return new MediaRefDto(key, storageService.publicUrl(StorageBucket.GARAGE, key));
    }

    private CarStatusOptionDto toStatusOptionDto(CarStatusOptionEntity s) {
        return new CarStatusOptionDto(s.getId(), s.getType());
    }

    private CarModificationDto toModificationDto(CarModificationEntity mod,
                                                   List<CarModificationGalleryEntity> media) {
        List<CarModificationMediaDto> mediaDtos = media.stream()
                .map(g -> new CarModificationMediaDto(g.getKey(), storageService.publicUrl(StorageBucket.GARAGE, g.getKey()), g.getType(), g.getPhase()))
                .toList();
        return new CarModificationDto(
                mod.getId(),
                mod.getCar().getId(),
                mod.getCategory().getId(),
                mod.getCategory().getModName(),
                mod.getTitle(),
                mod.getDescription(),
                mediaDtos,
                mod.getInstallationDate(),
                mod.getPrice(),
                mod.getMileageAtInstall(),
                mod.getCreatedAt());
    }

    private void insertModificationMedia(CarModificationEntity mod, String key, String phase) {
        CarModificationGalleryEntity media = new CarModificationGalleryEntity();
        media.setId(UUID.randomUUID());
        media.setModification(mod);
        media.setKey(key);
        media.setPhase(phase);
        media.setType(key.endsWith(".mp4") ? "video" : "image");
        modificationGalleryRepository.save(media);
    }
}