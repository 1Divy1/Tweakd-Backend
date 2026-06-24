package com.carsocialmedia.backend.report.internal.repositories;

import com.carsocialmedia.backend.report.internal.entities.CommentReportEntity;
import com.carsocialmedia.backend.report.internal.entities.CommentReportId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommentReportRepository extends JpaRepository<CommentReportEntity, CommentReportId> {

    boolean existsByIdCommentIdAndIdReporterId(UUID commentId, UUID reporterId);
}
