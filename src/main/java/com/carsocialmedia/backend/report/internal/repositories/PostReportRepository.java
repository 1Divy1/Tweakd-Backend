package com.carsocialmedia.backend.report.internal.repositories;

import com.carsocialmedia.backend.report.internal.entities.PostReportEntity;
import com.carsocialmedia.backend.report.internal.entities.PostReportId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostReportRepository extends JpaRepository<PostReportEntity, PostReportId> {

    boolean existsByIdPostIdAndIdReporterId(UUID postId, UUID reporterId);
}
