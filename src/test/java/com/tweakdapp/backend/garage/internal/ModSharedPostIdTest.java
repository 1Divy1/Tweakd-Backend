package com.tweakdapp.backend.garage.internal;

import com.tweakdapp.backend.garage.dto.CarDto;
import com.tweakdapp.backend.garage.dto.CarModificationDto;
import com.tweakdapp.backend.garage.internal.entities.CarBrandEntity;
import com.tweakdapp.backend.garage.internal.entities.CarColorEntity;
import com.tweakdapp.backend.garage.internal.entities.CarDistanceUnitEntity;
import com.tweakdapp.backend.garage.internal.entities.CarDrivetrainEntity;
import com.tweakdapp.backend.garage.internal.entities.CarEntity;
import com.tweakdapp.backend.garage.internal.entities.CarFuelTypeOptionsEntity;
import com.tweakdapp.backend.garage.internal.entities.CarModCategoryEntity;
import com.tweakdapp.backend.garage.internal.entities.CarModelEntity;
import com.tweakdapp.backend.garage.internal.entities.CarModificationEntity;
import com.tweakdapp.backend.garage.internal.entities.CarStatusOptionEntity;
import com.tweakdapp.backend.garage.internal.entities.GarageEntity;
import com.tweakdapp.backend.garage.internal.repositories.CarGalleryRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarModificationGalleryRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarModificationRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarRepository;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A car's build log says which of its mods have already been shared to the feed, so the app can
 * offer to share the ones that haven't and link to the post for the ones that have.
 *
 * The lookup crosses a module boundary the wrong way round — posts already depends on garage — so it
 * goes through {@code ModSharePostsProvider}, which is what these tests stand in for.
 */
class ModSharedPostIdTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID SHARED_MOD = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID UNSHARED_MOD = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID POST_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private CarRepository carRepository;
    private CarModificationRepository modificationRepository;
    private CarModificationGalleryRepository modificationGalleryRepository;
    private CarGalleryRepository carGalleryRepository;
    private ProfileService profileService;

    /** What the posts module would answer. */
    private Map<UUID, UUID> sharedPosts = Map.of();

    private GarageServiceImpl service;

    @BeforeEach
    void setUp() {
        carRepository = mock(CarRepository.class);
        modificationRepository = mock(CarModificationRepository.class);
        modificationGalleryRepository = mock(CarModificationGalleryRepository.class);
        carGalleryRepository = mock(CarGalleryRepository.class);
        profileService = mock(ProfileService.class);
        StorageService storageService = mock(StorageService.class);

        service = new GarageServiceImpl(
                null, carRepository, modificationRepository, modificationGalleryRepository,
                carGalleryRepository, null, null, null, null, null, null, null, null, null,
                null, profileService, storageService, null, null, null, carId -> List.of(),
                new StubModSharePosts(() -> sharedPosts));

        when(storageService.publicUrl(eq(StorageBucket.GARAGE), any()))
                .thenAnswer(inv -> "https://media.tweakdapp.com/" + inv.getArgument(1));

        CarEntity car = car();
        when(carRepository.findDetailById(CAR_ID)).thenReturn(Optional.of(car));
        when(modificationRepository.findByCarIdWithCategory(CAR_ID))
                .thenReturn(List.of(mod(car, SHARED_MOD, "H&R Coilovers"),
                                    mod(car, UNSHARED_MOD, "Titanium exhaust")));
        when(modificationGalleryRepository.findAllByCarId(CAR_ID)).thenReturn(List.of());
        when(carGalleryRepository.findAllByCarIdOrderByPositionAsc(CAR_ID)).thenReturn(List.of());
    }

    @Test
    void aSharedModCarriesItsPostIdAndAnUnsharedOneCarriesNone() {
        sharedPosts = Map.of(SHARED_MOD, POST_ID);

        CarDto car = service.getCar(OWNER.toString(), CAR_ID);

        assertThat(car.modifications())
                .extracting(CarModificationDto::id, CarModificationDto::sharedPostId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(SHARED_MOD, POST_ID),
                        org.assertj.core.groups.Tuple.tuple(UNSHARED_MOD, null));
    }

    @Test
    void aBuildLogWithNothingSharedReportsNoPostIds() {
        sharedPosts = Map.of();

        CarDto car = service.getCar(OWNER.toString(), CAR_ID);

        assertThat(car.modifications()).extracting(CarModificationDto::sharedPostId)
                .containsOnlyNulls();
    }

    // ---- fixtures -----------------------------------------------------------

    private static CarEntity car() {
        GarageEntity garage = new GarageEntity();
        garage.setId(UUID.randomUUID());
        garage.setOwnerId(OWNER);

        CarBrandEntity brand = new CarBrandEntity();
        brand.setId(UUID.randomUUID());
        brand.setName("BMW");

        CarModelEntity model = new CarModelEntity();
        model.setId(UUID.randomUUID());
        model.setBrand(brand);
        model.setModel("M3");

        CarDrivetrainEntity drivetrain = new CarDrivetrainEntity();
        drivetrain.setId("awd");
        drivetrain.setName("AWD");

        CarColorEntity color = new CarColorEntity();
        color.setId("frozen-black");
        color.setName("Frozen Black");
        color.setColorCode("#111111");

        CarDistanceUnitEntity unit = new CarDistanceUnitEntity();
        unit.setId("km");
        unit.setName("km");

        CarFuelTypeOptionsEntity fuel = new CarFuelTypeOptionsEntity();
        fuel.setId("petrol");
        fuel.setName("Petrol");

        CarStatusOptionEntity status = new CarStatusOptionEntity();
        status.setId("daily");
        status.setType("Daily Driver");

        CarEntity car = new CarEntity();
        car.setId(CAR_ID);
        car.setGarage(garage);
        car.setBrand(brand);
        car.setModel(model);
        car.setDrivetrain(drivetrain);
        car.setColor(color);
        car.setMileageUnit(unit);
        car.setFuelType(fuel);
        car.setStatus(status);
        car.setYear(2023);
        return car;
    }

    private static CarModificationEntity mod(CarEntity car, UUID id, String title) {
        CarModCategoryEntity category = new CarModCategoryEntity();
        category.setId("suspension");
        category.setModName("Suspension");

        CarModificationEntity mod = new CarModificationEntity();
        mod.setId(id);
        mod.setCar(car);
        mod.setCategory(category);
        mod.setTitle(title);
        mod.setInstallationDate(Instant.parse("2026-04-01T10:00:00Z"));
        return mod;
    }
}
