package com.tweakdapp.backend.garage.internal;

import com.tweakdapp.backend.garage.exception.CarNotFoundException;
import com.tweakdapp.backend.garage.exception.NotCarOwnerException;
import com.tweakdapp.backend.garage.internal.entities.CarEntity;
import com.tweakdapp.backend.garage.internal.repositories.CarRepository;
import com.tweakdapp.backend.storage.UploadAccessPolicy;
import com.tweakdapp.backend.storage.UploadTarget;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Only a car's owner may upload its cover, gallery or modification media. */
@Component
class CarUploadAccessPolicy implements UploadAccessPolicy {

    private final CarRepository carRepository;

    CarUploadAccessPolicy(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    @Override
    public UploadTarget target() {
        return UploadTarget.CAR;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireUploadAccess(UUID userId, UUID carId) {
        CarEntity car = carRepository.findById(carId)
                .orElseThrow(() -> new CarNotFoundException(carId));
        if (!car.getGarage().getOwnerId().equals(userId)) {
            throw new NotCarOwnerException();
        }
    }
}
