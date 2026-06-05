package com.carsocialmedia.backend.garage.internal;

import com.carsocialmedia.backend.follow.FollowService;
import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.CarBrandDto;
import com.carsocialmedia.backend.garage.dto.CarColorDto;
import com.carsocialmedia.backend.garage.dto.CarDistanceUnitDto;
import com.carsocialmedia.backend.garage.dto.CarDrivetrainDto;
import com.carsocialmedia.backend.garage.dto.CarDto;
import com.carsocialmedia.backend.garage.dto.CarModCategoryDto;
import com.carsocialmedia.backend.garage.dto.CarModelDto;
import com.carsocialmedia.backend.garage.dto.response.AddModificationResponse;
import com.carsocialmedia.backend.garage.dto.CarModificationDto;
import com.carsocialmedia.backend.garage.dto.CarModificationMediaDto;
import com.carsocialmedia.backend.garage.dto.request.CarModificationRequest;
import com.carsocialmedia.backend.garage.dto.request.CarRequest;
import com.carsocialmedia.backend.garage.dto.request.UpdateModificationRequest;
import com.carsocialmedia.backend.garage.dto.CarStatusOptionDto;
import com.carsocialmedia.backend.garage.dto.CarSummaryDto;
import com.carsocialmedia.backend.garage.dto.request.CreateCarRequest;
import com.carsocialmedia.backend.garage.dto.response.CreateCarResponse;
import com.carsocialmedia.backend.garage.dto.GarageDto;
import com.carsocialmedia.backend.garage.exception.CarModificationNotFoundException;
import com.carsocialmedia.backend.garage.exception.CarNotFoundException;
import com.carsocialmedia.backend.garage.exception.GarageNotFoundException;
import com.carsocialmedia.backend.garage.exception.InvalidReferenceException;
import com.carsocialmedia.backend.garage.exception.NotCarOwnerException;
import com.carsocialmedia.backend.garage.exception.PrivateGarageException;
import com.carsocialmedia.backend.garage.internal.entities.*;
import com.carsocialmedia.backend.garage.internal.repositories.*;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.exception.ProfileNotFoundException;
import com.carsocialmedia.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
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
    private final CarStatusOptionRepository statusOptionRepository;
    private final CarModCategoryRepository modCategoryRepository;
    private final ProfileService profileService;
    private final FollowService followService;
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
                      CarStatusOptionRepository statusOptionRepository,
                      CarModCategoryRepository modCategoryRepository,
                      ProfileService profileService,
                      FollowService followService,
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
        this.statusOptionRepository = statusOptionRepository;
        this.modCategoryRepository = modCategoryRepository;
        this.profileService = profileService;
        this.followService = followService;
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

        // Extract user's ID from the request
        UUID viewerId = UUID.fromString(currentUserId);

        // Check if the user exists
        UUID ownerId = profileService
                .findIdByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));

        // Verify that the viewer has permission to view (read-only) the garage based on privacy settings and follow
        // relationship
        ensureCanViewGarage(viewerId, ownerId, username);

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
        CarDto carDto = toCarDto(hydrated, mods, true);

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
        return toCarDto(car, mods, true);
    }

    @Override
    @Transactional
    public void deleteCar(String currentUserId, UUID carId) {
        UUID userId = UUID.fromString(currentUserId);

        CarEntity car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        ensureOwnership(car, userId);

        // car_modifications.car_id is ON DELETE CASCADE in Supabase.
        carRepository.delete(car);
    }

    @Override
    @Transactional(readOnly = true)
    public CarDto getCar(String currentUserId, UUID carId) {
        UUID viewerId = UUID.fromString(currentUserId);

        CarEntity car = carRepository.findDetailById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));

        UUID ownerId = car.getGarage().getOwnerId();
        boolean isOwner = viewerId.equals(ownerId);
        if (!isOwner) {
            ensureCanViewGarageById(viewerId, ownerId);
        }

        List<CarModificationEntity> mods = modificationRepository.findByCarIdWithCategory(carId);
        return toCarDto(car, mods, isOwner);
    }

    // -------------------------------------------------------------------
    // CAR MEDIA
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public void saveCarCoverImageUrl(String currentUserId, UUID carId, String url) {

        // Extract the user's ID from the request
        UUID userId = UUID.fromString(currentUserId);

        // Find the car in DB by its ID
        CarEntity car = carRepository
                .findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));

        // Verify that the current user is the owner of the car
        ensureOwnership(car, userId);

        // Update the cover image URL and save the changes in the DB
        car.setCoverImageUrl(url);
        carRepository.save(car);
    }

    @Override
    @Transactional
    public void saveGalleryImageUrls(String currentUserId, UUID carId, List<String> imageUrls) {
        UUID userId = UUID.fromString(currentUserId);

        CarEntity car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        ensureOwnership(car, userId);

        // Diff: find URLs that are in the DB but not in the incoming list — those are removed.
        Set<String> incomingSet = new HashSet<>(imageUrls);
        List<String> removedUrls = carGalleryRepository.findAllByCarIdOrderByPositionAsc(carId)
                .stream()
                .map(CarGalleryEntity::getUrl)
                .filter(url -> !incomingSet.contains(url))
                .toList();

        // DB: replace-all to persist the final ordered state.
        carGalleryRepository.deleteAllByCarId(carId);
        List<CarGalleryEntity> entries = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            CarGalleryEntity entry = new CarGalleryEntity();
            entry.setId(UUID.randomUUID());
            entry.setCar(car);
            entry.setUrl(imageUrls.get(i));
            entry.setPosition(i);
            entries.add(entry);
        }
        carGalleryRepository.saveAll(entries);

        // R2: delete removed objects only after the DB transaction commits.
        // If the DB rolls back, this callback never fires, so R2 is untouched.
        // If R2 deletion fails after commit, the DB is correct and we just log —
        // those files become orphans but no references point to them.
        if (!removedUrls.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        storageService.deleteObjects(removedUrls);
                    } catch (Exception e) {
                        log.warn("DB committed but failed to delete {} orphaned R2 objects for car {}",
                                removedUrls.size(), carId, e);
                    }
                }
            });
        }
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
        return new AddModificationResponse(toModificationDto(reloaded, List.of(), true));
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
        if (request.isPricePublic() != null)    mod.setPricePublic(request.isPricePublic());
        if (request.mileageAtInstall() != null) mod.setMileageAtInstall(request.mileageAtInstall());

        // Price consistency: if isPricePublic ends up true, a price must exist.
        if (mod.isPricePublic() && mod.getPrice() == null) {
            throw new InvalidReferenceException("price must be set when isPricePublic is true");
        }

        modificationRepository.save(mod);

        // Remove media items first so a re-upload of the same URL isn't double-inserted.
        if (request.removeMediaUrls() != null && !request.removeMediaUrls().isEmpty()) {
            modificationGalleryRepository.deleteAllByModification_IdAndUrlIn(modificationId, request.removeMediaUrls());
        }

        if (request.addMedia() != null) {
            for (UpdateModificationRequest.MediaItem item : request.addMedia()) {
                insertModificationMedia(mod, item.url(), item.phase());
            }
        }

        List<CarModificationGalleryEntity> media = modificationGalleryRepository.findAllByModification_Id(modificationId);
        return toModificationDto(mod, media, true);
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

        modificationRepository.delete(mod);
    }

    // -------------------------------------------------------------------
    // REFERENCE DATA
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CarBrandDto> listBrands() {
        return brandRepository.findAllByOrderByNameAsc().stream()
                .map(b -> new CarBrandDto(b.getId(), b.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarModelDto> listModelsByBrand(UUID brandId) {
        return modelRepository.findByBrandIdOrderByModelAsc(brandId).stream()
                .map(m -> new CarModelDto(m.getId(), m.getBrand().getId(), m.getModel()))
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

    // -------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------

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

        CarBrandEntity brand = brandRepository.findById(req.brandId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown brand id: " + req.brandId()));
        CarModelEntity model = modelRepository.findById(req.modelId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown model id: " + req.modelId()));

        // Reject inconsistent brand/model pairs — the DB lets this through but it'd produce
        // a nonsensical "BMW M3 with Honda brand" if the client mis-wired the dropdown.
        if (!model.getBrand().getId().equals(brand.getId())) {
            throw new InvalidReferenceException("Model " + req.modelId() + " does not belong to brand " + req.brandId());
        }
        CarDrivetrainEntity drivetrain = drivetrainRepository.findById(req.drivetrainId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown drivetrain id: " + req.drivetrainId()));
        CarColorEntity color = colorRepository.findById(req.colorId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown color id: " + req.colorId()));
        CarDistanceUnitEntity unit = distanceUnitRepository.findById(req.mileageUnitId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown mileage unit id: " + req.mileageUnitId()));
        CarStatusOptionEntity status = statusOptionRepository.findById(req.statusId())
                .orElseThrow(() -> new InvalidReferenceException("Unknown status id: " + req.statusId()));

        car.setBrand(brand);
        car.setModel(model);
        car.setDrivetrain(drivetrain);
        car.setColor(color);
        car.setMileageUnit(unit);
        car.setStatus(status);
        car.setYear(req.year());
        car.setHorsepower(req.horsepower());
        car.setTorque(req.torque());
        car.setWeight(req.weight());
        car.setEngineDisplacement(req.engineDisplacement());
        car.setZeroToOneHundred(req.zeroToOneHundred());
        car.setChassisCode(req.chassisCode());
        car.setEngineCode(req.engineCode());
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
        mod.setPricePublic(req.isPricePublic());
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
     * Verifies that the viewer has permission to access the garage based on the owner's
     * privacy settings and follow relationship.
     *
     * Rules:
     * <ul>
     *   <li>If viewer == owner: always allowed</li>
     *   <li>If owner's profile is public: always allowed</li>
     *   <li>If owner's profile is private: only allowed if viewer is an accepted follower</li>
     * </ul>
     *
     * @param viewerId the user ID requesting access
     * @param ownerId the garage owner's user ID
     * @param username the garage owner's username (for error message)
     * @throws PrivateGarageException if access is denied
     */
    private void ensureCanViewGarage(UUID viewerId, UUID ownerId, String username) {
        if (viewerId.equals(ownerId)) {
            return;
        }
        if (!profileService.isPrivate(ownerId)) {
            return;
        }
        if (!followService.isAcceptedFollower(viewerId, ownerId)) {
            throw new PrivateGarageException(username);
        }
    }

    /**
     * Verifies that the viewer has permission to access the garage based on the owner's
     * privacy settings and follow relationship. Same gate as {@link #ensureCanViewGarage}
     * but used when the username is not available.
     *
     * @param viewerId the user ID requesting access
     * @param ownerId the garage owner's user ID
     * @throws PrivateGarageException if access is denied
     */
    private void ensureCanViewGarageById(UUID viewerId, UUID ownerId) {
        if (!profileService.isPrivate(ownerId)) {
            return;
        }
        if (!followService.isAcceptedFollower(viewerId, ownerId)) {
            throw new PrivateGarageException(ownerId.toString());
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
    private GarageDto toGarageDto(GarageEntity garage) {
        List<CarSummaryDto> cars = carRepository.findGarageSummary(garage.getId()).stream()
                .map(c -> new CarSummaryDto(
                        c.getId(),
                        c.getBrand().getName(),
                        c.getModel().getModel(),
                        c.getCoverImageUrl(),
                        toStatusOptionDto(c.getStatus())))
                .toList();
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
     * lookups. Converts modification prices based on ownership and privacy settings.
     *
     * @param car the car entity (with all references eagerly loaded)
     * @param mods the list of modifications for this car
     * @param isOwner whether the current user is the car owner (used for price privacy)
     * @return the car DTO with all specifications and modifications
     */
    private CarDto toCarDto(CarEntity car, List<CarModificationEntity> mods, boolean isOwner) {
        // Load all media for this car's modifications in one query, then group by mod ID.
        Map<UUID, List<CarModificationGalleryEntity>> mediaByModId =
                modificationGalleryRepository.findAllByCarId(car.getId()).stream()
                        .collect(Collectors.groupingBy(g -> g.getModification().getId()));

        List<CarModificationDto> modDtos = mods.stream()
                .map(m -> toModificationDto(m, mediaByModId.getOrDefault(m.getId(), List.of()), isOwner))
                .toList();

        List<String> galleryUrls = carGalleryRepository.findAllByCarIdOrderByPositionAsc(car.getId()).stream()
                .map(CarGalleryEntity::getUrl)
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
                car.getEngineCode(),
                car.getCoverImageUrl(),
                galleryUrls,
                car.getCreatedAt(),
                toStatusOptionDto(car.getStatus()),
                modDtos);
    }

    private CarStatusOptionDto toStatusOptionDto(CarStatusOptionEntity s) {
        return new CarStatusOptionDto(s.getId(), s.getType());
    }

    /**
     * Converts a modification entity to a DTO, respecting price visibility.
     *
     * Price visibility rules:
     * <ul>
     *   <li>If current user is the car owner: price is always visible</li>
     *   <li>If modification is marked public: price is visible to everyone</li>
     *   <li>Otherwise: price is null (hidden from non-owners)</li>
     * </ul>
     *
     * @param mod the modification entity
     * @param isOwner whether the current user is the car owner
     * @return the modification DTO with privacy-controlled price visibility
     */
    private CarModificationDto toModificationDto(CarModificationEntity mod,
                                                   List<CarModificationGalleryEntity> media,
                                                   boolean isOwner) {
        List<CarModificationMediaDto> mediaDtos = media.stream()
                .map(g -> new CarModificationMediaDto(g.getUrl(), g.getType(), g.getPhase()))
                .toList();
        // Hide the price from non-owners when the owner marked it private.
        Float price = (isOwner || mod.isPricePublic()) ? mod.getPrice() : null;
        return new CarModificationDto(
                mod.getId(),
                mod.getCar().getId(),
                mod.getCategory().getId(),
                mod.getCategory().getModName(),
                mod.getTitle(),
                mod.getDescription(),
                mediaDtos,
                mod.getInstallationDate(),
                price,
                mod.isPricePublic(),
                mod.getMileageAtInstall(),
                mod.getCreatedAt());
    }

    private void insertModificationMedia(CarModificationEntity mod, String url, String phase) {
        CarModificationGalleryEntity media = new CarModificationGalleryEntity();
        media.setId(UUID.randomUUID());
        media.setModification(mod);
        media.setUrl(url);
        media.setPhase(phase);
        media.setType(url.endsWith(".mp4") ? "video" : "image");
        modificationGalleryRepository.save(media);
    }
}