package com.tweakdapp.backend.garage.internal;

import com.tweakdapp.backend.badges.dto.BadgeDto;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.garage.dto.CarShareDto;
import com.tweakdapp.backend.garage.dto.CarShareQrDto;
import com.tweakdapp.backend.garage.dto.CarShareResolutionDto;
import com.tweakdapp.backend.garage.dto.PublicCarDto;
import com.tweakdapp.backend.garage.dto.ShareSource;
import com.tweakdapp.backend.garage.exception.CarNotFoundException;
import com.tweakdapp.backend.garage.exception.NotCarOwnerException;
import com.tweakdapp.backend.garage.exception.ShareLinkGoneException;
import com.tweakdapp.backend.garage.exception.ShareLinkNotFoundException;
import com.tweakdapp.backend.garage.internal.entities.CarBrandEntity;
import com.tweakdapp.backend.garage.internal.entities.CarColorEntity;
import com.tweakdapp.backend.garage.internal.entities.CarDistanceUnitEntity;
import com.tweakdapp.backend.garage.internal.entities.CarDrivetrainEntity;
import com.tweakdapp.backend.garage.internal.entities.CarEntity;
import com.tweakdapp.backend.garage.internal.entities.CarFuelTypeOptionsEntity;
import com.tweakdapp.backend.garage.internal.entities.CarGalleryEntity;
import com.tweakdapp.backend.garage.internal.entities.CarModCategoryEntity;
import com.tweakdapp.backend.garage.internal.entities.CarModelEntity;
import com.tweakdapp.backend.garage.internal.entities.CarModificationEntity;
import com.tweakdapp.backend.garage.internal.entities.CarModificationGalleryEntity;
import com.tweakdapp.backend.garage.internal.entities.CarShareLinkEntity;
import com.tweakdapp.backend.garage.internal.entities.CarStatusOptionEntity;
import com.tweakdapp.backend.garage.internal.entities.GarageEntity;
import com.tweakdapp.backend.garage.internal.repositories.CarGalleryRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarModificationGalleryRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarModificationRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarShareLinkRepository;
import com.tweakdapp.backend.garage.internal.share.QrSvgRenderer;
import com.tweakdapp.backend.garage.internal.share.ShareCodeGenerator;
import com.tweakdapp.backend.garage.internal.share.SharingProperties;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.profile.dto.PublicProfileDto;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The share rules {@code GarageServiceImpl} owns, with the database mocked out.
 *
 * <p>Three of these carry real weight. Minting is idempotent, because a second code would orphan a
 * sticker somebody has already printed. The gate between 404 and 410 is exact, because a crawler
 * treats them differently and a paused link has to stop serving without losing its code. And the
 * public projection is checked structurally rather than field by field, so a car field added next
 * year fails this test instead of quietly appearing on the open internet.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CarShareServiceTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final String CODE = "7KQ3M9XA2F";

    @Mock private CarRepository carRepository;
    @Mock private CarShareLinkRepository shareLinkRepository;
    @Mock private CarModificationRepository modificationRepository;
    @Mock private CarModificationGalleryRepository modificationGalleryRepository;
    @Mock private CarGalleryRepository carGalleryRepository;
    @Mock private ProfileService profileService;
    @Mock private StorageService storageService;
    @Mock private ShareCodeGenerator shareCodeGenerator;

    private GarageServiceImpl service;
    private SharingProperties sharingProperties;
    private List<com.tweakdapp.backend.garage.dto.PublicCarEventDto> events = List.of();

    @BeforeEach
    void setUp() {
        sharingProperties = new SharingProperties();
        sharingProperties.setPublicBaseUrl("https://web.tweakdapp.com/c");

        service = new GarageServiceImpl(
                null, carRepository, modificationRepository, modificationGalleryRepository,
                carGalleryRepository, null, null, null, null, null, null, null, null, null,
                shareLinkRepository, profileService, storageService,
                shareCodeGenerator, new QrSvgRenderer(), sharingProperties, carId -> events);

        when(storageService.publicUrl(eq(StorageBucket.GARAGE), any()))
                .thenAnswer(inv -> "https://media.tweakdapp.com/" + inv.getArgument(1));
        when(shareCodeGenerator.next()).thenReturn(CODE);
        when(shareLinkRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shareLinkRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- minting ------------------------------------------------------------

    /**
     * The guarantee the share sheet depends on: it POSTs on every open, and every open after the
     * first has to hand back the code already printed on the car.
     */
    @Test
    void ensureShareLinkReturnsTheExistingCodeInsteadOfMintingASecond() {
        CarEntity car = car();
        when(carRepository.findById(CAR_ID)).thenReturn(Optional.of(car));
        when(shareLinkRepository.findLiveByCarId(CAR_ID)).thenReturn(Optional.of(link(car, CODE)));

        CarShareDto share = service.ensureShareLink(OWNER.toString(), CAR_ID);

        assertThat(share.code()).isEqualTo(CODE);
        verify(shareLinkRepository, never()).saveAndFlush(any());
        verify(shareCodeGenerator, never()).next();
    }

    @Test
    void ensureShareLinkMintsOnFirstUse() {
        CarEntity car = car();
        when(carRepository.findById(CAR_ID)).thenReturn(Optional.of(car));
        when(shareLinkRepository.findLiveByCarId(CAR_ID)).thenReturn(Optional.empty());
        when(shareLinkRepository.existsByCode(CODE)).thenReturn(false);

        CarShareDto share = service.ensureShareLink(OWNER.toString(), CAR_ID);

        ArgumentCaptor<CarShareLinkEntity> saved = ArgumentCaptor.forClass(CarShareLinkEntity.class);
        verify(shareLinkRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo(CODE);
        assertThat(saved.getValue().getOwnerId()).isEqualTo(OWNER);
        assertThat(saved.getValue().isEnabled()).isTrue();
        assertThat(saved.getValue().getRevokedAt()).isNull();

        assertThat(share.url()).isEqualTo("https://web.tweakdapp.com/c/" + CODE);
        assertThat(share.qrUrl()).isEqualTo("https://web.tweakdapp.com/c/" + CODE + "?s=qr");
        assertThat(share.enabled()).isTrue();
    }

    /**
     * Two taps of "Share" race past the existsByCode pre-check; the database's partial unique index
     * decides. The loser must find the winner's row rather than surface a constraint violation, or
     * the owner sees an error on a button that in fact worked.
     */
    @Test
    void ensureShareLinkRecoversWhenAConcurrentCallWinsTheInsert() {
        CarEntity car = car();
        CarShareLinkEntity winner = link(car, "AAAAAAAAAA");
        when(carRepository.findById(CAR_ID)).thenReturn(Optional.of(car));
        when(shareLinkRepository.findLiveByCarId(CAR_ID))
                .thenReturn(Optional.empty())          // the pre-check
                .thenReturn(Optional.of(winner));      // after the collision
        when(shareLinkRepository.existsByCode(any())).thenReturn(false);
        when(shareLinkRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("car_share_links_active_car_uq"));

        CarShareDto share = service.ensureShareLink(OWNER.toString(), CAR_ID);

        assertThat(share.code()).isEqualTo("AAAAAAAAAA");
    }

    /** Every owner route runs the same gate; a stranger cannot even learn whether a car is shared. */
    @Test
    void everyOwnerRouteRejectsANonOwner() {
        CarEntity car = car();
        when(carRepository.findById(CAR_ID)).thenReturn(Optional.of(car));
        when(shareLinkRepository.findLiveByCarId(CAR_ID)).thenReturn(Optional.of(link(car, CODE)));
        String stranger = STRANGER.toString();

        assertThatExceptionOfType(NotCarOwnerException.class)
                .isThrownBy(() -> service.ensureShareLink(stranger, CAR_ID));
        assertThatExceptionOfType(NotCarOwnerException.class)
                .isThrownBy(() -> service.getShareLink(stranger, CAR_ID));
        assertThatExceptionOfType(NotCarOwnerException.class)
                .isThrownBy(() -> service.setShareLinkEnabled(stranger, CAR_ID, false));
        assertThatExceptionOfType(NotCarOwnerException.class)
                .isThrownBy(() -> service.renderShareQrSvg(stranger, CAR_ID));
    }

    @Test
    void ownerRoutesOnAMissingCarAre404() {
        when(carRepository.findById(CAR_ID)).thenReturn(Optional.empty());
        String owner = OWNER.toString();

        assertThatExceptionOfType(CarNotFoundException.class)
                .isThrownBy(() -> service.ensureShareLink(owner, CAR_ID));
    }

    @Test
    void getShareLinkIs404BeforeTheCarHasEverBeenShared() {
        CarEntity car = car();
        when(carRepository.findById(CAR_ID)).thenReturn(Optional.of(car));
        when(shareLinkRepository.findLiveByCarId(CAR_ID)).thenReturn(Optional.empty());
        String owner = OWNER.toString();

        assertThatExceptionOfType(ShareLinkNotFoundException.class)
                .isThrownBy(() -> service.getShareLink(owner, CAR_ID));
    }

    // ---- pause / resume / revoke -------------------------------------------

    /** Pausing must not touch the code — that is what lets a printed sticker come back to life. */
    @Test
    void pausingAndResumingKeepTheSameCode() {
        CarEntity car = car();
        CarShareLinkEntity existing = link(car, CODE);
        when(carRepository.findById(CAR_ID)).thenReturn(Optional.of(car));
        when(shareLinkRepository.findLiveByCarId(CAR_ID)).thenReturn(Optional.of(existing));

        CarShareDto paused = service.setShareLinkEnabled(OWNER.toString(), CAR_ID, false);
        assertThat(paused.enabled()).isFalse();
        assertThat(paused.code()).isEqualTo(CODE);

        CarShareDto resumed = service.setShareLinkEnabled(OWNER.toString(), CAR_ID, true);
        assertThat(resumed.enabled()).isTrue();
        assertThat(resumed.code()).isEqualTo(CODE);
        assertThat(existing.getRevokedAt()).isNull();
    }

    @Test
    void revokingStampsTheRowAndIsIdempotent() {
        CarEntity car = car();
        CarShareLinkEntity existing = link(car, CODE);
        when(shareLinkRepository.findLiveByCarId(CAR_ID))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.empty());

        assertThat(service.revokeShareLinksForCar(CAR_ID)).isEqualTo(1);
        assertThat(existing.getRevokedAt()).isNotNull();

        assertThat(service.revokeShareLinksForCar(CAR_ID)).isZero();
    }

    // ---- QR -----------------------------------------------------------------

    /** The QR must encode the tagged URL, or a scan is indistinguishable from a tapped link. */
    @Test
    void theQrEncodesTheTaggedShareUrl() {
        CarEntity car = car();
        when(carRepository.findById(CAR_ID)).thenReturn(Optional.of(car));
        when(shareLinkRepository.findLiveByCarId(CAR_ID)).thenReturn(Optional.of(link(car, CODE)));

        CarShareQrDto qr = service.renderShareQrSvg(OWNER.toString(), CAR_ID);

        assertThat(qr.code()).isEqualTo(CODE);
        assertThat(new String(qr.svg())).isEqualTo(
                new QrSvgRenderer().render("https://web.tweakdapp.com/c/" + CODE + "?s=qr"));
    }

    // ---- resolving ----------------------------------------------------------

    @Test
    void resolvingReturnsTheCarAndItsOwnersUsername() {
        CarEntity car = car();
        when(shareLinkRepository.findByCode(CODE)).thenReturn(Optional.of(link(car, CODE)));
        when(profileService.isBanned(OWNER)).thenReturn(false);
        when(profileService.findByIds(List.of(OWNER)))
                .thenReturn(List.of(new ProfileSearchResultDto(OWNER, "Dave", "dave", null)));

        CarShareResolutionDto resolved =
                service.resolveShareCode(STRANGER.toString(), CODE, ShareSource.LINK);

        assertThat(resolved.carId()).isEqualTo(CAR_ID);
        assertThat(resolved.ownerUsername()).isEqualTo("dave");
    }

    /** A code typed off a scratched sticker, lowercase and with an O for a zero, still resolves. */
    @Test
    void aMistypedCodeIsCanonicalisedBeforeLookup() {
        CarEntity car = car();
        when(shareLinkRepository.findByCode("0123456789")).thenReturn(Optional.of(link(car, "0123456789")));
        when(profileService.isBanned(OWNER)).thenReturn(false);

        assertThat(service.resolveShareCode(STRANGER.toString(), "o123-456 789", ShareSource.LINK))
                .isNotNull();
        assertThat(service.getPublicCar("O123456789", ShareSource.LINK, false)).isNotNull();
    }

    /** Malformed input is 404, not 400: to the person holding the sticker it is the same dead link. */
    @Test
    void malformedAndUnknownCodesAreBoth404() {
        when(shareLinkRepository.findByCode(any())).thenReturn(Optional.empty());

        assertThatExceptionOfType(ShareLinkNotFoundException.class)
                .isThrownBy(() -> service.getPublicCar("not-a-code", ShareSource.LINK, true));
        assertThatExceptionOfType(ShareLinkNotFoundException.class)
                .isThrownBy(() -> service.getPublicCar("ZZZZZZZZZZ", ShareSource.LINK, true));
        assertThatExceptionOfType(ShareLinkNotFoundException.class)
                .isThrownBy(() -> service.getPublicCar("../../etc/passwd", ShareSource.LINK, true));
    }

    /** 410 rather than 404 for all three, so a crawler drops the page instead of retrying it. */
    @Test
    void pausedRevokedAndBannedAllAnswerGone() {
        CarEntity car = car();

        CarShareLinkEntity paused = link(car, CODE);
        paused.setEnabled(false);
        when(shareLinkRepository.findByCode(CODE)).thenReturn(Optional.of(paused));
        when(profileService.isBanned(OWNER)).thenReturn(false);
        assertThatExceptionOfType(ShareLinkGoneException.class)
                .isThrownBy(() -> service.getPublicCar(CODE, ShareSource.LINK, true));

        CarShareLinkEntity revoked = link(car, CODE);
        revoked.setRevokedAt(Instant.now());
        when(shareLinkRepository.findByCode(CODE)).thenReturn(Optional.of(revoked));
        assertThatExceptionOfType(ShareLinkGoneException.class)
                .isThrownBy(() -> service.getPublicCar(CODE, ShareSource.LINK, true));

        when(shareLinkRepository.findByCode(CODE)).thenReturn(Optional.of(link(car, CODE)));
        when(profileService.isBanned(OWNER)).thenReturn(true);
        assertThatExceptionOfType(ShareLinkGoneException.class)
                .isThrownBy(() -> service.getPublicCar(CODE, ShareSource.LINK, true));
    }

    /** A dead link must not be counted, or the pause switch becomes a way to inflate a number. */
    @Test
    void aLinkThatCannotServeIsNeverCounted() {
        CarEntity car = car();
        CarShareLinkEntity paused = link(car, CODE);
        paused.setEnabled(false);
        when(shareLinkRepository.findByCode(CODE)).thenReturn(Optional.of(paused));

        assertThatExceptionOfType(ShareLinkGoneException.class)
                .isThrownBy(() -> service.getPublicCar(CODE, ShareSource.QR, true));
        verify(shareLinkRepository, never()).recordView(any(), anyInt(), anyInt(), any());
    }

    // ---- counting -----------------------------------------------------------

    @Test
    void aScanCountsAsAScanAndATapCountsAsAView() {
        CarEntity car = car();
        when(shareLinkRepository.findByCode(CODE)).thenReturn(Optional.of(link(car, CODE)));
        when(profileService.isBanned(OWNER)).thenReturn(false);

        service.getPublicCar(CODE, ShareSource.QR, true);
        verify(shareLinkRepository).recordView(any(), eq(0), eq(1), any());

        service.getPublicCar(CODE, ShareSource.LINK, true);
        verify(shareLinkRepository).recordView(any(), eq(1), eq(0), any());
    }

    /** The response is unaffected; only the counter is. */
    @Test
    void aCrawlerGetsThePageButIsNotCounted() {
        CarEntity car = car();
        when(shareLinkRepository.findByCode(CODE)).thenReturn(Optional.of(link(car, CODE)));
        when(profileService.isBanned(OWNER)).thenReturn(false);

        assertThat(service.getPublicCar(CODE, ShareSource.LINK, false)).isNotNull();
        verify(shareLinkRepository, never()).recordView(any(), anyInt(), anyInt(), any());
    }

    /** An in-app open of a scanned sticker counts like a web one — the sticker did the work. */
    @Test
    void resolvingInTheAppCountsToo() {
        CarEntity car = car();
        when(shareLinkRepository.findByCode(CODE)).thenReturn(Optional.of(link(car, CODE)));
        when(profileService.isBanned(OWNER)).thenReturn(false);

        service.resolveShareCode(STRANGER.toString(), CODE, ShareSource.QR);

        verify(shareLinkRepository, times(1)).recordView(any(), eq(0), eq(1), any());
    }

    // ---- the public projection ---------------------------------------------

    @Test
    void thePublicPageCarriesTheBuildAndTheOwnerCard() {
        CarEntity car = car();
        when(shareLinkRepository.findByCode(CODE)).thenReturn(Optional.of(link(car, CODE)));
        when(profileService.isBanned(OWNER)).thenReturn(false);
        when(profileService.findPublicProfileById(OWNER)).thenReturn(Optional.of(publicProfile()));

        PublicCarDto page = service.getPublicCar(CODE, ShareSource.QR, true);

        assertThat(page.code()).isEqualTo(CODE);
        assertThat(page.url()).isEqualTo("https://web.tweakdapp.com/c/" + CODE);
        assertThat(page.brandName()).isEqualTo("BMW");
        assertThat(page.modelName()).isEqualTo("M5");
        assertThat(page.year()).isEqualTo(2022);
        assertThat(page.horsepower()).isEqualTo(635);
        assertThat(page.coverImageUrl()).isEqualTo("https://media.tweakdapp.com/cars/cover.jpg");
        assertThat(page.galleryUrls()).containsExactly("https://media.tweakdapp.com/cars/g1.jpg");

        assertThat(page.modifications()).singleElement().satisfies(mod -> {
            assertThat(mod.title()).isEqualTo("H&R Coilovers");
            assertThat(mod.categoryName()).isEqualTo("Suspension");
            assertThat(mod.price()).isEqualTo(1200);
            assertThat(mod.priceCurrency()).isEqualTo("EUR");
            assertThat(mod.media()).singleElement().satisfies(media -> {
                assertThat(media.url()).isEqualTo("https://media.tweakdapp.com/mods/after.jpg");
                assertThat(media.phase()).isEqualTo("after");
            });
        });

        assertThat(page.events()).isEmpty();
        assertThat(page.owner().username()).isEqualTo("dave");
        assertThat(page.owner().reputationScore()).isEqualTo(420);
        assertThat(page.owner().isVerified()).isTrue();
        assertThat(page.owner().badges()).singleElement().satisfies(badge -> {
            assertThat(badge.name()).isEqualTo("Pioneer");
            assertThat(badge.imageUrl()).isEqualTo("https://assets.tweakd.app/pioneer.svg");
        });
    }

    /** Events come from Map Events through the provider, in one response with the rest of the car. */
    @Test
    void thePublicPageCarriesTheCarsEventsAndContestBadges() {
        CarEntity car = car();
        when(shareLinkRepository.findByCode(CODE)).thenReturn(Optional.of(link(car, CODE)));
        when(profileService.isBanned(OWNER)).thenReturn(false);
        when(profileService.findPublicProfileById(OWNER)).thenReturn(Optional.of(publicProfile()));
        events = List.of(new com.tweakdapp.backend.garage.dto.PublicCarEventDto(
                "Waterside Show", null, "Waterside Quay", Instant.parse("2026-05-24T10:00:00Z"), "previous",
                List.of(new com.tweakdapp.backend.garage.dto.PublicCarPlacementDto("Best modified", "trophy", 1))));

        PublicCarDto page = service.getPublicCar(CODE, ShareSource.LINK, false);

        assertThat(page.events()).singleElement().satisfies(event -> {
            assertThat(event.title()).isEqualTo("Waterside Show");
            assertThat(event.placements()).singleElement().satisfies(p -> assertThat(p.rank()).isEqualTo(1));
        });
    }

    /**
     * The structural half of D8, and the one that survives someone adding a field to the car next
     * year: the public shape must not grow a component whose name looks like an internal id, a
     * storage key or a plate. Widening the projection then fails here rather than silently
     * publishing a licence plate to the open internet.
     */
    @Test
    void thePublicShapeExposesNoIdentifiersOrStorageKeys() {
        List<String> forbidden = List.of(
                "id", "carId", "garageId", "ownerId", "profileId", "userId",
                "licensePlate", "plate", "key", "coverImageKey", "latitude", "longitude", "city");

        assertNoComponentsNamed(PublicCarDto.class, forbidden);
        assertNoComponentsNamed(com.tweakdapp.backend.garage.dto.PublicCarModificationDto.class, forbidden);
        assertNoComponentsNamed(com.tweakdapp.backend.garage.dto.PublicCarOwnerDto.class, forbidden);
        assertNoComponentsNamed(com.tweakdapp.backend.garage.dto.PublicBadgeDto.class, forbidden);
        assertNoComponentsNamed(com.tweakdapp.backend.garage.dto.PublicMediaDto.class, forbidden);
        assertNoComponentsNamed(com.tweakdapp.backend.garage.dto.PublicCarEventDto.class, forbidden);
        assertNoComponentsNamed(com.tweakdapp.backend.garage.dto.PublicCarPlacementDto.class, forbidden);
    }

    private static void assertNoComponentsNamed(Class<?> record, List<String> forbidden) {
        for (RecordComponent component : record.getRecordComponents()) {
            assertThat(forbidden)
                    .as("%s.%s is on the public car page", record.getSimpleName(), component.getName())
                    .doesNotContain(component.getName());
        }
    }

    // ---- fixtures -----------------------------------------------------------

    private CarEntity car() {
        GarageEntity garage = new GarageEntity();
        garage.setId(UUID.fromString("00000000-0000-0000-0000-0000000000d1"));
        garage.setOwnerId(OWNER);

        CarBrandEntity brand = new CarBrandEntity();
        brand.setId(UUID.randomUUID());
        brand.setName("BMW");

        CarModelEntity model = new CarModelEntity();
        model.setId(UUID.randomUUID());
        model.setBrand(brand);
        model.setModel("M5");

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
        car.setYear(2022);
        car.setHorsepower(635);
        car.setTorque(750);
        car.setWeight(1900);
        car.setMileage(12_000);
        car.setEngineDisplacement(4.4f);
        car.setStory("Bought it after ten years of saving.");
        car.setCoverImageKey("cars/cover.jpg");

        stubCarDetailReads(car);
        return car;
    }

    /** Wires up the reads {@code toCarDto} makes, so the projection runs against a real CarDto. */
    private void stubCarDetailReads(CarEntity car) {
        CarModCategoryEntity category = new CarModCategoryEntity();
        category.setId("suspension");
        category.setModName("Suspension");

        CarModificationEntity mod = new CarModificationEntity();
        mod.setId(UUID.fromString("00000000-0000-0000-0000-0000000000e1"));
        mod.setCar(car);
        mod.setCategory(category);
        mod.setTitle("H&R Coilovers");
        mod.setDescription("Dropped 30mm.");
        mod.setInstallationDate(Instant.parse("2026-04-01T10:00:00Z"));
        mod.setPrice(1200);
        mod.setPriceCurrency("EUR");
        // The fixture's owner publishes this price. The opposite -- the default -- is asserted by
        // aPriceTheOwnerHasNotPublishedIsNotOnThePublicPage.
        mod.setPricePublic(true);
        mod.setMileageAtInstall(9_000);

        CarModificationGalleryEntity media = new CarModificationGalleryEntity();
        media.setId(UUID.randomUUID());
        media.setModification(mod);
        media.setKey("mods/after.jpg");
        media.setType("image");
        media.setPhase("after");

        CarGalleryEntity gallery = new CarGalleryEntity();
        gallery.setId(UUID.randomUUID());
        gallery.setCar(car);
        gallery.setKey("cars/g1.jpg");
        gallery.setPosition(0);

        when(carRepository.findDetailById(CAR_ID)).thenReturn(Optional.of(car));
        when(modificationRepository.findByCarIdWithCategory(CAR_ID)).thenReturn(List.of(mod));
        when(modificationGalleryRepository.findAllByCarId(CAR_ID)).thenReturn(List.of(media));
        when(carGalleryRepository.findAllByCarIdOrderByPositionAsc(CAR_ID)).thenReturn(List.of(gallery));
    }

    @Test
    void aPriceTheOwnerHasNotPublishedIsNotOnThePublicPage() {
        CarEntity car = car();
        // Same fixture, price kept private -- the default for every mod.
        modificationRepository.findByCarIdWithCategory(CAR_ID).forEach(m -> m.setPricePublic(false));
        when(shareLinkRepository.findByCode(CODE)).thenReturn(Optional.of(link(car, CODE)));
        when(profileService.isBanned(OWNER)).thenReturn(false);
        when(profileService.findPublicProfileById(OWNER)).thenReturn(Optional.of(publicProfile()));

        PublicCarDto page = service.getPublicCar(CODE, ShareSource.QR, true);

        assertThat(page.modifications()).singleElement().satisfies(mod -> {
            // The build still reads in full; only what it cost is withheld.
            assertThat(mod.title()).isEqualTo("H&R Coilovers");
            assertThat(mod.price()).isNull();
            assertThat(mod.priceCurrency()).isNull();
        });
    }

    private CarShareLinkEntity link(CarEntity car, String code) {
        CarShareLinkEntity link = new CarShareLinkEntity();
        link.setId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
        link.setCar(car);
        link.setOwnerId(OWNER);
        link.setCode(code);
        link.setEnabled(true);
        return link;
    }

    private PublicProfileDto publicProfile() {
        BadgeDto pioneer = new BadgeDto("pioneer", "Pioneer", "One of the first.",
                "https://assets.tweakd.app/pioneer.svg", null, true,
                "account_created", null, null,
                Instant.parse("2026-01-01T00:00:00Z"));
        return new PublicProfileDto(
                OWNER, "Dave", "dave", "https://avatars.tweakdapp.com/dave.jpg",
                "bio", "https://example.com", 12, 34, true, false, 420,
                List.of(new UserBadgeDto(pioneer, Instant.parse("2026-02-01T00:00:00Z"))));
    }
}
