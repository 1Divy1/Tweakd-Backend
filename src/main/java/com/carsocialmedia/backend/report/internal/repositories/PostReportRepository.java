package com.carsocialmedia.backend.report.internal.repositories;

import com.carsocialmedia.backend.report.internal.entities.PostReportEntity;
import com.carsocialmedia.backend.report.internal.entities.PostReportId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostReportRepository extends JpaRepository<PostReportEntity, PostReportId> {

    boolean existsByIdPostIdAndIdReporterId(UUID postId, UUID reporterId);

    /** All post reports filed by the given reporter (for their "my reports" feed). */
    List<PostReportEntity> findByIdReporterId(UUID reporterId);

    /** All reports filed against the given post (for the admin moderation view). */
    List<PostReportEntity> findByIdPostId(UUID postId);
}
