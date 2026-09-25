package com.tweakdapp.backend.garage.internal;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.ModSharePostsProvider;
import com.tweakdapp.backend.garage.PublicCarEventsProvider;
import com.tweakdapp.backend.garage.dto.CarBrandDto;
import com.tweakdapp.backend.garage.dto.CarColorDto;
import com.tweakdapp.backend.garage.dto.CarDistanceUnitDto;
import com.tweakdapp.backend.garage.dto.CarFuelTypeOptionsDto;
import com.tweakdapp.backend.garage.dto.CarDrivetrainDto;
import com.tweakdapp.backend.garage.dto.CarDto;
import com.tweakdapp.backend.garage.dto.CarModCategoryDto;
import com.tweakdapp.backend.garage.dto.CarModelDto;
import com.tweakdapp.backend.garage.dto.CarOwnerDto;
import com.tweakdapp.backend.garage.dto.CarShareDto;
import com.tweakdapp.backend.garage.dto.CarShareQrDto;
import com.tweakdapp.backend.garage.dto.CarShareResolutionDto;
import com.tweakdapp.backend.garage.dto.PublicBadgeDto;
import com.tweakdapp.backend.garage.dto.PublicCarDto;
import com.tweakdapp.backend.garage.dto.PublicCarEventDto;
import com.tweakdapp.backend.garage.dto.PublicCarModificationDto;
import com.tweakdapp.backend.garage.dto.PublicCarOwnerDto;
import com.tweakdapp.backend.garage.dto.PublicMediaDto;
import com.tweakdapp.backend.garage.dto.ShareSource;
import com.tweakdapp.backend.garage.dto.response.AddModificationResponse;
import com.tweakdapp.backend.garage.dto.CarModificationDto;
import com.tweakdapp.backend.garage.dto.CarModificationMediaDto;
import com.tweakdapp.backend.garage.dto.request.CarModificationRequest;
import com.tweakdapp.backend.garage.dto.request.CarRequest;
import com.tweakdapp.backend.garage.dto.request.UpdateModificationRequest;
import com.tweakdapp.backend.garage.dto.CarStatusOptionDto;
import com.tweakdapp.backend.garage.dto.MediaRefDto;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.garage.dto.DreamCarDto;
import com.tweakdapp.backend.garage.dto.request.CreateCarRequest;
import com.tweakdapp.backend.garage.dto.request.DreamCarRequest;
import com.tweakdapp.backend.garage.dto.request.DreamCarRequestBody;
import com.tweakdapp.backend.garage.dto.response.CreateCarResponse;
import com.tweakdapp.backend.garage.dto.GarageDto;
import com.tweakdapp.backend.garage.dto.ModShareCardDto;
import com.tweakdapp.backend.garage.exception.CarModificationNotFoundException;
import com.tweakdapp.backend.garage.exception.CarNotFoundException;
import com.tweakdapp.backend.garage.exception.DreamCarNotFoundException;
import com.tweakdapp.backend.garage.exception.GarageNotFoundException;
import com.tweakdapp.backend.garage.exception.InvalidReferenceException;
import com.tweakdapp.backend.garage.exception.NotCarOwnerException;
import com.tweakdapp.backend.garage.exception.ShareLinkGoneException;
import com.tweakdapp.backend.garage.exception.ShareLinkNotFoundException;
import com.tweakdapp.backend.garage.internal.entities.*;
import com.tweakdapp.backend.garage.internal.repositories.*;
import com.tweakdapp.backend.garage.internal.share.QrSvgRenderer;
import com.tweakdapp.backend.garage.internal.share.ShareCodeGenerator;
import com.tweakdapp.backend.garage.internal.share.SharingProperties;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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
    private final CarShareLinkRepository shareLinkRepository;
    private final ProfileService profileService;
    private final StorageService storageService;
    private final ShareCodeGenerator shareCodeGenerator;
    private final QrSvgRenderer qrSvgRenderer;
    private final SharingProperties sharingProperties;
    private final PublicCarEventsProvider publicCarEvents;
    private final ModSharePostsProvider modSharePosts;

    /** How many times minting a share code retries past a unique-index collision. See {@code ensureLink}. */
    private static final int CODE_INSERT_ATTEMPTS = 3;

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
                      CarShareLinkRepository shareLinkRepository,
                      ProfileService profileService,
                      StorageService storageService,
                      ShareCodeGenerator shareCodeGenerator,
                      QrSvgRenderer qrSvgRenderer,
                      SharingProperties sharingProperties,
                      // Lazy: the implementation lives in Map Events, whose services depend on
                      // GarageService. An eager reference would be a bean cycle.
                      @Lazy PublicCarEventsProvider publicCarEvents,
                      // Lazy for the same reason: the implementation lives in posts, which
                      // depends on this service.
                      @Lazy ModSharePostsProvider modSharePosts) {
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
        this.shareLinkRepository = shareLinkRepository;
        this.profileService = profileService;
        this.storageService = storageService;
        this.shareCodeGenerator = shareCodeGenerator;
        this.qrSvgRenderer = qrSvgRenderer;
        this.sharingProperties = sharingProperties;
        this.publicCarEvents = publicCarEvents;
        this.modSharePosts = modSharePosts;
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
                .findVisibleIdByUsername(UUID.fromString(currentUserId), username)
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

        // Collect every R2 object owned by this car BEFORE the DB rows disappear: the cover image,
        // all gallery images, and all media of every modification. The DB child rows are removed by
        // the Supabase ON DELETE CASCADE, but that cascade only touches the database, not R2.
        List<String> r2Urls = new ArrayList<>();
        if (car.getCoverImageKey() != null) {
            r2Urls.add(car.getCoverImageKey());
        }
        // Keys only, never entities: a managed gallery row still pointing at the car once it is
        // removed makes Hibernate refuse to flush (TransientPropertyValueException -> 500).
        r2Urls.addAll(carGalleryRepository.findKeysByCarId(carId));
        r2Urls.addAll(modificationGalleryRepository.findKeysByCarId(carId));

        // Feed posts sharing this car's mods go with it, in this transaction. The FK would only null
        // their mod reference, leaving cards with nothing left to draw.
        modSharePosts.deleteSharePosts(modificationRepository.findIdsByCarId(carId));

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

        // A car whose owner a block separates from the viewer reads as missing.
        if (currentUserId != null) {
            UUID ownerId = findCarOwnerIds(List.of(carId)).get(carId);
            if (ownerId != null && profileService.isHiddenFrom(UUID.fromString(currentUserId), ownerId)) {
                throw new CarNotFoundException(carId);
            }
        }

        List<CarModificationEntity> mods = modificationRepository.findByCarIdWithCategory(carId);
        boolean forOwner = currentUserId != null
                && car.getGarage().getOwnerId().equals(UUID.fromString(currentUserId));
        return toCarDto(car, mods, forOwner);
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

        String previous = car.getCoverImageKey();
        requireOwnedKeys(List.of(key), previous == null ? List.of() : List.of(previous),
                "cars/" + carId + "/cover/");

        // Persist the R2 key (not the full URL) and save the changes in the DB
        car.setCoverImageKey(key);
        carRepository.save(car);

        // Only ever delete an object under this car's own prefix, even if an older row says otherwise.
        if (previous != null && !previous.equals(key) && previous.startsWith("cars/" + carId + "/")) {
            deleteR2ObjectsAfterCommit(List.of(previous), carId);
        }
    }

    /**
     * Upload URLs mint keys under the resource they were issued for. A key that is neither already
     * stored on the resource nor under {@code prefix} was minted for someone else, and accepting it
     * would let the caller attach, and later delete, another user's object.
     */
    private static void requireOwnedKeys(List<String> incoming, List<String> stored, String prefix) {
        for (String key : incoming) {
            if (key == null || (!stored.contains(key) && !key.startsWith(prefix))) {
                throw new InvalidReferenceException("Media key does not belong to this car: " + key);
            }
        }
    }

    @Override
    @Transactional
    public void saveGalleryImageKeys(String currentUserId, UUID carId, List<String> imageKeys) {
        UUID userId = UUID.fromString(currentUserId);

        CarEntity car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        ensureOwnership(car, userId);

        List<String> storedKeys = carGalleryRepository.findAllByCarIdOrderByPositionAsc(carId)
                .stream()
                .map(CarGalleryEntity::getKey)
                .toList();
        requireOwnedKeys(imageKeys, storedKeys, "cars/" + carId + "/gallery/");

        // Diff: find keys that are in the DB but not in the incoming list — those are removed.
        Set<String> incomingSet = new HashSet<>(imageKeys);
        List<String> removedKeys = storedKeys.stream()
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
        // A mod created one statement ago cannot have been shared yet.
        return new AddModificationResponse(toModificationDto(reloaded, List.of(), true, null));
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

        // The DB refuses a published price that isn't there; fail with a 400 rather than a 500.
        if (mod.isPricePublic() && mod.getPrice() == null) {
            throw new InvalidReferenceException("isPricePublic requires a price");
        }

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
            List<String> addedKeys = request.addMedia().stream()
                    .map(UpdateModificationRequest.MediaItem::key)
                    .toList();
            requireOwnedKeys(addedKeys, List.of(), "cars/" + carId + "/modifications/" + modificationId + "/");
            for (UpdateModificationRequest.MediaItem item : request.addMedia()) {
                insertModificationMedia(mod, item.key(), item.phase());
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
    // SHARE LINKS
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public CarShareDto ensureShareLink(String currentUserId, UUID carId) {
        CarEntity car = loadOwnedCar(currentUserId, carId);
        return toShareDto(ensureLink(car));
    }

    @Override
    @Transactional(readOnly = true)
    public CarShareDto getShareLink(String currentUserId, UUID carId) {
        loadOwnedCar(currentUserId, carId);
        return shareLinkRepository.findLiveByCarId(carId)
                .map(this::toShareDto)
                .orElseThrow(ShareLinkNotFoundException::new);
    }

    @Override
    @Transactional
    public CarShareDto setShareLinkEnabled(String currentUserId, UUID carId, boolean enabled) {
        CarEntity car = loadOwnedCar(currentUserId, carId);
        CarShareLinkEntity link = ensureLink(car);
        link.setEnabled(enabled);
        return toShareDto(shareLinkRepository.save(link));
    }

    @Override
    @Transactional
    public CarShareQrDto renderShareQrSvg(String currentUserId, UUID carId) {
        CarEntity car = loadOwnedCar(currentUserId, carId);
        CarShareLinkEntity link = ensureLink(car);
        return new CarShareQrDto(
                link.getCode(),
                qrSvgRenderer.renderBytes(sharingProperties.qrUrlFor(link.getCode())));
    }

    @Override
    @Transactional
    public int revokeShareLinksForCar(UUID carId) {
        // Deliberately no ownership check: the only caller is a transfer, which runs after the car
        // has already changed hands and so has no "current owner" to check against.
        return shareLinkRepository.findLiveByCarId(carId)
                .map(link -> {
                    link.setRevokedAt(Instant.now());
                    shareLinkRepository.save(link);
                    log.info("Revoked share link {} on car {}", link.getCode(), carId);
                    return 1;
                })
                .orElse(0);
    }

    @Override
    @Transactional
    public CarShareResolutionDto resolveShareCode(String currentUserId, String rawCode, ShareSource source) {
        CarShareLinkEntity link = resolveServableLink(rawCode);
        recordVisit(link, source);

        UUID ownerId = link.getCar().getGarage().getOwnerId();
        String username = profileService.findByIds(List.of(ownerId)).stream()
                .findFirst()
                .map(ProfileSearchResultDto::username)
                .orElse(null);

        return new CarShareResolutionDto(link.getCar().getId(), username);
    }

    @Override
    @Transactional
    public PublicCarDto getPublicCar(String rawCode, ShareSource source, boolean countView) {
        CarShareLinkEntity link = resolveServableLink(rawCode);
        if (countView) {
            recordVisit(link, source);
        }

        UUID carId = link.getCar().getId();
        CarEntity car = carRepository.findDetailById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        List<CarModificationEntity> mods = modificationRepository.findByCarIdWithCategory(carId);

        // Project from the in-app DTO rather than from the entity: the mapping from entity to
        // CarDto is already proven, and going through it means the public shape is visibly a
        // subset. A field added to CarDto reaches the open internet only if somebody adds it to
        // toPublicCarDto too.
        CarDto carDto = toCarDto(car, mods, false);
        return toPublicCarDto(carDto, link, car.getGarage().getOwnerId(), publicCarEvents.findForCar(carId));
    }

    // ---- share helpers ------------------------------------------------------

    /** Loads a car and asserts the caller owns it — the gate on every owner-facing share method. */
    private CarEntity loadOwnedCar(String currentUserId, UUID carId) {
        CarEntity car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        ensureOwnership(car, UUID.fromString(currentUserId));
        return car;
    }

    /**
     * The car's live link, minting one on first use.
     *
     * <p>The generate-and-insert is retried because {@code existsByCode} is a read followed by a
     * write: two callers can both pass it. The database's unique index is what actually decides,
     * and a lost race just means one more attempt out of 1.1e15 possibilities.
     */
    private CarShareLinkEntity ensureLink(CarEntity car) {
        Optional<CarShareLinkEntity> existing = shareLinkRepository.findLiveByCarId(car.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        for (int attempt = 1; attempt <= CODE_INSERT_ATTEMPTS; attempt++) {
            String code = shareCodeGenerator.next();
            if (shareLinkRepository.existsByCode(code)) {
                continue;
            }
            CarShareLinkEntity link = new CarShareLinkEntity();
            link.setId(UUID.randomUUID());
            link.setCar(car);
            link.setOwnerId(car.getGarage().getOwnerId());
            link.setCode(code);
            link.setEnabled(true);
            try {
                CarShareLinkEntity saved = shareLinkRepository.saveAndFlush(link);
                log.info("Minted share code {} for car {}", code, car.getId());
                return saved;
            } catch (DataIntegrityViolationException e) {
                // Either the code collided or another request minted this car's link first. Both
                // are decided by a unique index, and both are fixed by going round again: the
                // second pass finds the live row and returns it.
                log.debug("Share link insert lost a race for car {} (attempt {})", car.getId(), attempt);
                Optional<CarShareLinkEntity> raced = shareLinkRepository.findLiveByCarId(car.getId());
                if (raced.isPresent()) {
                    return raced.get();
                }
            }
        }
        throw new IllegalStateException("Could not mint a share code for car " + car.getId());
    }

    /**
     * Turns a code from the outside world into a link that is allowed to serve a page, or throws
     * the status the caller should return.
     *
     * <p>Unknown or malformed is 404; paused, revoked or owned by a banned account is 410. The
     * difference exists for crawlers — a 410 gets dropped from an index, a 404 gets retried — and
     * the human-facing page is the same either way.
     */
    private CarShareLinkEntity resolveServableLink(String rawCode) {
        CarShareLinkEntity link = ShareCodeGenerator.normalize(rawCode)
                .flatMap(shareLinkRepository::findByCode)
                .orElseThrow(ShareLinkNotFoundException::new);

        if (link.getRevokedAt() != null || !link.isEnabled()) {
            throw new ShareLinkGoneException();
        }
        // BannedUserInterceptor only sees authenticated requests, so the public page has to make
        // this check itself — otherwise a banned account keeps a public shopfront.
        if (profileService.isBanned(link.getCar().getGarage().getOwnerId())) {
            throw new ShareLinkGoneException();
        }
        return link;
    }

    /** Counts one visit against the link, in the database, in the bucket the source tag chose. */
    private void recordVisit(CarShareLinkEntity link, ShareSource source) {
        boolean qr = source == ShareSource.QR;
        shareLinkRepository.recordView(link.getId(), qr ? 0 : 1, qr ? 1 : 0, Instant.now());
    }

    private CarShareDto toShareDto(CarShareLinkEntity link) {
        return new CarShareDto(
                link.getCode(),
                sharingProperties.urlFor(link.getCode()),
                sharingProperties.qrUrlFor(link.getCode()),
                link.isEnabled(),
                link.getCreatedAt(),
                link.getViewCount(),
                link.getQrScanCount(),
                link.getLastViewedAt());
    }

    /**
     * The allow-list that is the public page. Every field here was chosen; anything not written out
     * below is not on the internet. Notably absent: the car's id, the garage id, the owner's UUID,
     * the licence plate, the owner's location, and every R2 object key.
     */
    private PublicCarDto toPublicCarDto(CarDto car, CarShareLinkEntity link, UUID ownerId,
                                        List<PublicCarEventDto> events) {
        List<String> galleryUrls = car.gallery().stream()
                .map(MediaRefDto::url)
                .filter(Objects::nonNull)
                .toList();

        List<PublicCarModificationDto> mods = car.modifications().stream()
                .map(m -> new PublicCarModificationDto(
                        m.categoryName(),
                        m.title(),
                        m.description(),
                        m.media().stream()
                                .map(media -> new PublicMediaDto(media.url(), media.type(), media.phase()))
                                .toList(),
                        m.installationDate(),
                        m.price(),
                        m.priceCurrency(),
                        m.mileageAtInstall()))
                .toList();

        return new PublicCarDto(
                link.getCode(),
                sharingProperties.urlFor(link.getCode()),
                car.brandName(),
                car.modelName(),
                car.year(),
                car.modelCode(),
                car.chassisCode(),
                car.engineCode(),
                car.horsepower(),
                car.torque(),
                car.weight(),
                car.engineDisplacement(),
                car.zeroToOneHundred(),
                car.drivetrainName(),
                car.colorName(),
                car.colorCode(),
                car.fuelTypeName(),
                car.statusName(),
                car.mileage(),
                car.mileageUnitName(),
                car.story(),
                car.coverImage() == null ? null : car.coverImage().url(),
                galleryUrls,
                mods,
                events,
                toPublicOwnerDto(ownerId),
                link.getCreatedAt());
    }

    /**
     * The owner card. Reputation and badges are on it deliberately: a stranger deciding whether to
     * trust a build sheet has nothing else to go on, and it is the same credibility signal the app
     * shows.
     */
    private PublicCarOwnerDto toPublicOwnerDto(UUID ownerId) {
        return profileService.findPublicProfileById(ownerId)
                .map(profile -> new PublicCarOwnerDto(
                        profile.username(),
                        profile.name(),
                        profile.avatarUrl(),
                        profile.isVerified(),
                        profile.reputationScore(),
                        profile.badges().stream()
                                .map(b -> new PublicBadgeDto(b.badge().title(), b.badge().unlockedUrl()))
                                .toList()))
                .orElse(null);
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
        mod.setPricePublic(Boolean.TRUE.equals(req.isPricePublic()));
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

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findCarIdsByOwner(UUID ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        return carRepository.findIdsByOwnerId(ownerId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ModShareCardDto> findModShareCards(Collection<UUID> modificationIds) {
        if (modificationIds == null || modificationIds.isEmpty()) {
            return Map.of();
        }

        List<CarModificationEntity> mods =
                modificationRepository.findAllByIdsWithCategoryAndCar(modificationIds);
        if (mods.isEmpty()) {
            return Map.of();
        }

        // Three batched queries for the whole page: the mods, their media, and their cars.
        List<UUID> modIds = mods.stream().map(CarModificationEntity::getId).toList();
        Map<UUID, List<CarModificationGalleryEntity>> mediaByModId = modificationGalleryRepository
                .findAllByModificationIds(modIds).stream()
                .collect(Collectors.groupingBy(g -> g.getModification().getId()));

        Set<UUID> carIds = mods.stream().map(m -> m.getCar().getId()).collect(Collectors.toSet());
        Map<UUID, CarSummaryDto> cars = findCarsByIds(carIds).stream()
                .collect(Collectors.toMap(CarSummaryDto::id, Function.identity()));

        Map<UUID, ModShareCardDto> byModId = new HashMap<>();
        for (CarModificationEntity mod : mods) {
            CarSummaryDto car = cars.get(mod.getCar().getId());
            if (car == null) {
                // The car went while the post stayed. Nothing to draw a card around.
                continue;
            }
            List<CarModificationMediaDto> media = mediaByModId.getOrDefault(mod.getId(), List.of()).stream()
                    .map(g -> new CarModificationMediaDto(
                            g.getKey(),
                            storageService.publicUrl(StorageBucket.GARAGE, g.getKey()),
                            g.getType(),
                            g.getPhase()))
                    .toList();
            // The card is read by everyone, so it is assembled as a non-owner would see it: an
            // unpublished price is absent from the payload, not hidden by the client.
            Integer price = visiblePrice(mod, false);
            byModId.put(mod.getId(), new ModShareCardDto(
                    mod.getId(),
                    car,
                    mod.getCategory().getModName(),
                    mod.getTitle(),
                    mod.getDescription(),
                    mediaOfPhase(media, "before"),
                    mediaOfPhase(media, "after"),
                    mod.getInstallationDate(),
                    price,
                    price == null ? null : mod.getPriceCurrency(),
                    mod.getMileageAtInstall()));
        }
        return byModId;
    }

    /** Phase values are stored lowercase, but a case-insensitive match costs nothing to be safe. */
    private static List<CarModificationMediaDto> mediaOfPhase(List<CarModificationMediaDto> media, String phase) {
        return media.stream()
                .filter(m -> phase.equalsIgnoreCase(m.phase()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findOwnedModificationCarId(UUID ownerId, UUID modificationId) {
        if (ownerId == null || modificationId == null) {
            return Optional.empty();
        }
        return modificationRepository.findCarIdByIdAndOwner(modificationId, ownerId);
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
     * @param forOwner whether the reader owns the car; false masks unpublished mod prices
     * @return the car DTO with all specifications and modifications
     */
    private CarDto toCarDto(CarEntity car, List<CarModificationEntity> mods, boolean forOwner) {

        // Load all media for this car's modifications in one query, then group by mod ID.
        Map<UUID, List<CarModificationGalleryEntity>> mediaByModId = modificationGalleryRepository
                .findAllByCarId(car.getId()).stream()
                .collect(Collectors.groupingBy(g -> g.getModification().getId()));

        // One lookup for the whole build log, rather than one per mod.
        Map<UUID, UUID> sharedPostIds = modSharePosts.findPostIdsByModificationIds(
                mods.stream().map(CarModificationEntity::getId).toList());

        List<CarModificationDto> modDtos = mods
                .stream()
                .map(m -> toModificationDto(
                        m,
                        mediaByModId.getOrDefault(m.getId(), List.of()),
                        forOwner,
                        sharedPostIds.get(m.getId())))
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
                c.getYear(),
                c.getHorsepower(),
                c.getTorque(),
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

    /**
     * @param forOwner whether the reader is the car's owner. False hides a price the owner has not
     *        published — the one field on a mod that is not public by default.
     */
    private CarModificationDto toModificationDto(CarModificationEntity mod,
                                                   List<CarModificationGalleryEntity> media,
                                                   boolean forOwner) {
        return toModificationDto(mod, media, forOwner, sharedPostIdOf(mod.getId()));
    }

    /** One lookup, for the single-mod write paths. The car read resolves a whole page at once. */
    private UUID sharedPostIdOf(UUID modId) {
        return modSharePosts.findPostIdsByModificationIds(List.of(modId)).get(modId);
    }

    private CarModificationDto toModificationDto(CarModificationEntity mod,
                                                   List<CarModificationGalleryEntity> media,
                                                   boolean forOwner,
                                                   UUID sharedPostId) {
        List<CarModificationMediaDto> mediaDtos = media.stream()
                .map(g -> new CarModificationMediaDto(g.getKey(), storageService.publicUrl(StorageBucket.GARAGE, g.getKey()), g.getType(), g.getPhase()))
                .toList();
        Integer price = visiblePrice(mod, forOwner);
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
                price == null ? null : mod.getPriceCurrency(),
                mod.isPricePublic(),
                mod.getMileageAtInstall(),
                mod.getCreatedAt(),
                sharedPostId);
    }

    /** A mod's price as the given reader may see it: the owner always, others only if published. */
    private static Integer visiblePrice(CarModificationEntity mod, boolean forOwner) {
        return forOwner || mod.isPricePublic() ? mod.getPrice() : null;
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