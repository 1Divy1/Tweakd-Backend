package com.carsocialmedia.backend.mapevents.internal.repositories;

import com.carsocialmedia.backend.mapevents.internal.entities.CarMeetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CarMeetRepository extends JpaRepository<CarMeetEntity, UUID> {
}
