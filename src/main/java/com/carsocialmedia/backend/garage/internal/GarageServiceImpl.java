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
import com.carsocialmedia.backend.garage.dto.AddModificationResponse;
import com.carsocialmedia.backend.garage.dto.CarModificationDto;
import com.carsocialmedia.backend.garage.dto.CarModificationRequest;
import com.carsocialmedia.backend.garage.dto.CarRequest;
import com.carsocialmedia.backend.garage.dto.CarStatusOptionDto;
import com.carsocialmedia.backend.garage.dto.CarSummaryDto;
import com.carsocialmedia.backend.garage.dto.CreateCarRequest;
import com.carsocialmedia.backend.garage.dto.CreateCarResponse;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
class GarageServiceImpl implements GarageService {

    private final GarageRepository garageRepository;
    private final CarRepository carRepository;
    private final CarModificationRepository modificationRepository;
    private final CarBrandRepository brandRepository;
    private final CarModelRepository modelRepository;
    private final CarDrivetrainRepository drivetrainRepository;
    private final CarColorRepository colorRepository;
    private final CarDistanceUnitRepository distanceUnitRepository;
    private final CarStatusOptionRepository statusOptionRepository;
    private final CarModCategoryRepository modCategoryRepository;
    private final ProfileService profileService;
    private final FollowService followService;

    @PersistenceContext
    private EntityManager entityManager;

    GarageServiceImpl(GarageRepository garageRepository,
                      CarRepository carRepository,
                      CarModificationRepository modificationRepository,
                      CarBrandRepository brandRepository,
                      CarModelRepository modelRepository,
                      CarDrivetrainRepository drivetrainRepository,
                      CarColorRepository colorRepository,
                      CarDistanceUnitRepository distanceUnitRepository,
                      CarStatusOptionRepository statusOptionRepository,
                      CarModCategoryRepository modCategoryRepository,
                      ProfileService profileService,
                      FollowService followService) {
        this.garageRepository = garageRepository;
        this.carRepository = carRepository;
        this.modificationRepository = modificationRepository;
        this.brandRepository = brandRepository;
        this.modelRepository = modelRepository;
        this.drivetrainRepository = drivetrainRepository;
        this.colorRepository = colorRepository;
        this.distanceUnitRepository = distanceUnitRepository;
        this.statusOptionRepository = statusOptionRepository;
        this.modCategoryRepository = modCategoryRepository;
        this.profileService = profileService;
        this.followService = followService;
    }

    // -------------------------------------------------------------------
    // garage views
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
        UUID viewerId = UUID.fromString(currentUserId);
        UUID ownerId = profileService.findIdByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));

        ensureCanViewGarage(viewerId, ownerId, username);

        GarageEntity garage = garageRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> GarageNotFoundException.forOwner(ownerId.toString()));
        return toGarageDto(garage);
    }

    // -------------------------------------------------------------------
    // cars
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
    // modifications
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
        // Flush the INSERT and reload from the DB so the DB-managed createdAt
        // is populated. findById alone returns the cached instance without a
        // round-trip, leaving createdAt null in the response.
        entityManager.flush();
        entityManager.refresh(mod);

        return new AddModificationResponse(toModificationDto(mod, true));
    }

    @Override
    @Transactional
    public CarModificationDto updateModification(String currentUserId, UUID carId, UUID modificationId, CarModificationRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        CarModificationEntity mod = modificationRepository.findById(modificationId)
                .orElseThrow(() -> new CarModificationNotFoundException(modificationId));

        if (!mod.getCar().getId().equals(carId)) {
            throw new CarModificationNotFoundException(modificationId);
        }
        ensureOwnership(mod.getCar(), userId);

        applyModificationRequest(mod, request);
        modificationRepository.save(mod);
        return toModificationDto(mod, true);
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
    // reference data
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
    // helpers
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
        List<CarModificationDto> modDtos = mods.stream()
                .map(m -> toModificationDto(m, isOwner))
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
                car.getYear(),
                car.getHorsepower(),
                car.getTorque(),
                car.getWeight(),
                car.getEngineDisplacement(),
                car.getZeroToOneHundred(),
                car.getChassisCode(),
                car.getEngineCode(),
                car.getCoverImageUrl(),
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
    private CarModificationDto toModificationDto(CarModificationEntity mod, boolean isOwner) {
        // Hide the price from non-owners when the owner marked it private.
        Float price = (isOwner || mod.isPricePublic()) ? mod.getPrice() : null;
        return new CarModificationDto(
                mod.getId(),
                mod.getCar().getId(),
                mod.getCategory().getId(),
                mod.getCategory().getModName(),
                mod.getTitle(),
                mod.getDescription(),
                mod.getBeforeImageUrl(),
                mod.getAfterImageUrl(),
                mod.getInstallationDate(),
                price,
                mod.isPricePublic(),
                mod.getMileageAtInstall(),
                mod.getCreatedAt());
    }
}