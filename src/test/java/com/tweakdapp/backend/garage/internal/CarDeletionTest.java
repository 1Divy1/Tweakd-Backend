package com.tweakdapp.backend.garage.internal;

import com.tweakdapp.backend.garage.exception.NotCarOwnerException;
import com.tweakdapp.backend.garage.internal.entities.CarEntity;
import com.tweakdapp.backend.garage.internal.entities.GarageEntity;
import com.tweakdapp.backend.garage.internal.repositories.CarGalleryRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarModificationGalleryRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarModificationRepository;
import com.tweakdapp.backend.garage.internal.repositories.CarRepository;
import com.tweakdapp.backend.storage.StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A mod-share post is a card drawn from the build log. Deleting the whole car takes those posts
 * down in the same transaction, before the cascade removes the mods they point at.
 */
class CarDeletionTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID MOD_A = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID MOD_B = UUID.fromString("00000000-0000-0000-0000-0000000000d2");

    private CarRepository carRepository;
    private CarModificationRepository modificationRepository;
    private StubModSharePosts modSharePosts;
    private GarageServiceImpl service;
    private CarEntity car;

    @BeforeEach
    void setUp() {
        carRepository = mock(CarRepository.class);
        modificationRepository = mock(CarModificationRepository.class);
        modSharePosts = StubModSharePosts.none();

        service = new GarageServiceImpl(
                null, carRepository, modificationRepository, mock(CarModificationGalleryRepository.class),
                mock(CarGalleryRepository.class), null, null, null, null, null, null, null, null, null,
                null, null, mock(StorageService.class), null, null, null, null,
                modSharePosts);

        GarageEntity garage = new GarageEntity();
        garage.setOwnerId(OWNER);
        car = new CarEntity();
        car.setId(CAR_ID);
        car.setGarage(garage);
        when(carRepository.findById(CAR_ID)).thenReturn(Optional.of(car));
        when(modificationRepository.findIdsByCarId(CAR_ID)).thenReturn(List.of(MOD_A, MOD_B));

        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void deletingACarTakesItsModSharePostsDownFirst() {
        // Once the car row goes the mods cascade away, and the posts could no longer be found by them.
        doAnswer(inv -> {
            assertThat(modSharePosts.deleteCalls).containsExactly(List.of(MOD_A, MOD_B));
            return null;
        }).when(carRepository).delete(car);

        service.deleteCar(OWNER.toString(), CAR_ID);

        verify(carRepository).delete(car);
        assertThat(modSharePosts.deleteCalls).containsExactly(List.of(MOD_A, MOD_B));
    }

    @Test
    void aStrangerCannotDeleteTheCarOrItsPosts() {
        assertThatThrownBy(() -> service.deleteCar(STRANGER.toString(), CAR_ID))
                .isInstanceOf(NotCarOwnerException.class);

        verify(carRepository, never()).delete(any());
        assertThat(modSharePosts.deleteCalls).isEmpty();
    }
}
