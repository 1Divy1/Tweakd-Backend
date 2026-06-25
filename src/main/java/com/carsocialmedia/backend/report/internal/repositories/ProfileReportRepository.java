package com.carsocialmedia.backend.report.internal.repositories;

import com.carsocialmedia.backend.report.internal.entities.ProfileReportEntity;
import com.carsocialmedia.backend.report.internal.entities.ProfileReportId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProfileReportRepository extends JpaRepository<ProfileReportEntity, ProfileReportId> {

    boolean existsByIdProfileIdAndIdReporterId(UUID profileId, UUID reporterId);
}
