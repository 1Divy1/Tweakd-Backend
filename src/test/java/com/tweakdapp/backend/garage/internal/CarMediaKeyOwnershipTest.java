package com.tweakdapp.backend.garage.internal;

import com.tweakdapp.backend.garage.dto.request.UpdateModificationRequest;
import com.tweakdapp.backend.garage.exception.CarNotFoundException;
import com.tweakdapp.backend.garage.exception.InvalidReferenceException;
import com.tweakdapp.backend.garage.exception.NotCarOwnerException;
import com.tweakdapp.backend.garage.internal.entities.CarEntity;
import com.tweakdapp.backend.garage.internal.entities.CarGalleryEntity;
import com.tweakdapp.backend.garage.internal.entities.CarModCategoryEntity;
import com.tweakdapp.backend.garage.internal.entities.CarModificationEntity;
import com.tweakdapp.backend.garage.internal.entities.GarageEntity;
import com.tweakdapp.backend.garage.internal.repositories.CarGalleryRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarModificationGalleryRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarModificationRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarRepository;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Car media keys come back from the client, and the garage deletes R2 objects by the keys it has
 * stored. Accepting a key minted for another car would let an owner attach that object and then
 * delete it from R2 by removing it, so every attach path only takes keys under the car's own prefix
 * (or ones already stored on it).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CarMediaKeyOwnershipTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OTHER_CAR = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID MOD_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @Mock private CarRepository carRepository;
    @Mock private CarModificationRepository modificationRepository;
    @Mock private CarModificationGalleryRepository modificationGalleryRepository;
    @Mock private CarGalleryRepository carGalleryRepository;
    @Mock private StorageService storageService;

    private GarageServiceImpl service;
    private CarEntity car;

    @BeforeEach
    void setUp() {
        service = new GarageServiceImpl(
                null, carRepository, modificationRepository, modificationGalleryRepository,
                carGalleryRepository, null, null, null, null, null, null, null, null, null,
                null, null, storageService, null, null, null, null);

        GarageEntity garage = new GarageEntity();
        garage.setOwnerId(OWNER);
        car = new CarEntity();
        car.setId(CAR_ID);
        car.setGarage(garage);
        when(carRepository.findById(CAR_ID)).thenReturn(Optional.of(car));

        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ---- cover ----------------------------------------------------------------

    @Test
    void aCoverKeyMintedForAnotherCarIsRejected() {
        assertThatThrownBy(() -> service.saveCarCoverImageKey(OWNER.toString(), CAR_ID,
                "cars/" + OTHER_CAR + "/cover.webp"))
                .isInstanceOf(InvalidReferenceException.class);

        assertThat(car.getCoverImageKey()).isNull();
        verify(carRepository, never()).save(any());
    }

    @Test
    void aNewCoverReplacesTheOldOneAndDeletesItsObjectAfterCommit() {
        String old = "cars/" + CAR_ID + "/cover.webp";
        String replacement = "cars/" + CAR_ID + "/cover/" + UUID.randomUUID() + ".webp";
        car.setCoverImageKey(old);

        service.saveCarCoverImageKey(OWNER.toString(), CAR_ID, replacement);
        commit();

        assertThat(car.getCoverImageKey()).isEqualTo(replacement);
        verify(storageService).deleteByKeys(StorageBucket.GARAGE, List.of(old));
    }

    @Test
    void resavingTheStoredCoverIsAcceptedAndDeletesNothing() {
        String legacy = "cars/" + CAR_ID + "/cover.webp";
        car.setCoverImageKey(legacy);

        service.saveCarCoverImageKey(OWNER.toString(), CAR_ID, legacy);
        commit();

        verify(storageService, never()).deleteByKeys(any(), any());
    }

    @Test
    void aStoredCoverOutsideTheCarsPrefixIsNeverDeleted() {
        car.setCoverImageKey("cars/" + OTHER_CAR + "/gallery/" + UUID.randomUUID() + ".webp");

        service.saveCarCoverImageKey(OWNER.toString(), CAR_ID,
                "cars/" + CAR_ID + "/cover/" + UUID.randomUUID() + ".webp");
        commit();

        verify(storageService, never()).deleteByKeys(any(), any());
    }

    // ---- gallery --------------------------------------------------------------

    @Test
    void aGalleryKeyMintedForAnotherCarIsRejectedBeforeAnythingIsRemoved() {
        String own = "cars/" + CAR_ID + "/gallery/a.webp";
        when(carGalleryRepository.findAllByCarIdOrderByPositionAsc(CAR_ID)).thenReturn(List.of(galleryRow(own)));

        assertThatThrownBy(() -> service.saveGalleryImageKeys(OWNER.toString(), CAR_ID,
                List.of("cars/" + OTHER_CAR + "/gallery/b.webp")))
                .isInstanceOf(InvalidReferenceException.class);
        commit();

        verify(carGalleryRepository, never()).deleteAllByCarId(any());
        verify(storageService, never()).deleteByKeys(any(), any());
    }

    @Test
    void galleryKeysAlreadyOnTheCarOrUnderItsPrefixAreSaved() {
        String stored = "legacy/" + CAR_ID + "/a.webp";
        when(carGalleryRepository.findAllByCarIdOrderByPositionAsc(CAR_ID)).thenReturn(List.of(galleryRow(stored)));

        service.saveGalleryImageKeys(OWNER.toString(), CAR_ID,
                List.of(stored, "cars/" + CAR_ID + "/gallery/b.webp"));

        verify(carGalleryRepository).saveAll(any());
    }

    // ---- modification media ----------------------------------------------------

    @Test
    void modificationMediaMintedForAnotherModificationIsRejected() {
        when(modificationRepository.findById(MOD_ID)).thenReturn(Optional.of(modification()));

        assertThatThrownBy(() -> service.patchModification(OWNER.toString(), CAR_ID, MOD_ID,
                addMedia("cars/" + CAR_ID + "/modifications/" + UUID.randomUUID() + "/before/x.webp")))
                .isInstanceOf(InvalidReferenceException.class);

        verify(modificationGalleryRepository, never()).save(any());
    }

    @Test
    void modificationMediaUnderItsOwnPrefixIsAttached() {
        when(modificationRepository.findById(MOD_ID)).thenReturn(Optional.of(modification()));

        assertThatCode(() -> service.patchModification(OWNER.toString(), CAR_ID, MOD_ID,
                addMedia("cars/" + CAR_ID + "/modifications/" + MOD_ID + "/before/x.webp")))
                .doesNotThrowAnyException();

        verify(modificationGalleryRepository).save(any());
    }

    // ---- upload URL policy -----------------------------------------------------

    @Test
    void onlyTheOwnerIsHandedCarUploadUrls() {
        CarUploadAccessPolicy policy = new CarUploadAccessPolicy(carRepository);
        UUID missing = UUID.randomUUID();
        when(carRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatCode(() -> policy.requireUploadAccess(OWNER, CAR_ID)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireUploadAccess(STRANGER, CAR_ID))
                .isInstanceOf(NotCarOwnerException.class);
        assertThatThrownBy(() -> policy.requireUploadAccess(OWNER, missing))
                .isInstanceOf(CarNotFoundException.class);
    }

    private CarModificationEntity modification() {
        CarModificationEntity mod = new CarModificationEntity();
        mod.setId(MOD_ID);
        mod.setCar(car);
        mod.setCategory(org.mockito.Mockito.mock(CarModCategoryEntity.class));
        return mod;
    }

    private static UpdateModificationRequest addMedia(String key) {
        return new UpdateModificationRequest(null, null, null, null, null, null, null,
                List.of(new UpdateModificationRequest.MediaItem(key, "before")), null);
    }

    private CarGalleryEntity galleryRow(String key) {
        CarGalleryEntity row = new CarGalleryEntity();
        row.setId(UUID.randomUUID());
        row.setCar(car);
        row.setKey(key);
        return row;
    }

    private static void commit() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }
}
