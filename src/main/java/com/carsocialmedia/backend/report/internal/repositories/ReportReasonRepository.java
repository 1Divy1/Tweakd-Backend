package com.carsocialmedia.backend.report.internal.repositories;

import com.carsocialmedia.backend.report.internal.entities.ReportReasonEntity;
import com.carsocialmedia.backend.report.internal.enums.ReportTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReportReasonRepository extends JpaRepository<ReportReasonEntity, UUID> {

    List<ReportReasonEntity> findAllByTarget(ReportTarget target);
}
