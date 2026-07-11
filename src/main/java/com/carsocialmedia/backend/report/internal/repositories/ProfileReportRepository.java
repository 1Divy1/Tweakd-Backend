package com.carsocialmedia.backend.report.internal.repositories;

import com.carsocialmedia.backend.report.internal.entities.ProfileReportEntity;
import com.carsocialmedia.backend.report.internal.entities.ProfileReportId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileReportRepository extends JpaRepository<ProfileReportEntity, ProfileReportId> {

    boolean existsByIdProfileIdAndIdReporterId(UUID profileId, UUID reporterId);

    /** All profile reports filed by the given reporter (for their "my reports" feed). */
    List<ProfileReportEntity> findByIdReporterId(UUID reporterId);

    /** All reports filed against the given profile (for the admin moderation view). */
    List<ProfileReportEntity> findByIdProfileId(UUID profileId);
}
