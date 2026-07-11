package com.carsocialmedia.backend.report.internal.repositories;

import com.carsocialmedia.backend.report.internal.entities.CommentReportEntity;
import com.carsocialmedia.backend.report.internal.entities.CommentReportId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentReportRepository extends JpaRepository<CommentReportEntity, CommentReportId> {

    boolean existsByIdCommentIdAndIdReporterId(UUID commentId, UUID reporterId);

    /** All comment reports filed by the given reporter (for their "my reports" feed). */
    List<CommentReportEntity> findByIdReporterId(UUID reporterId);

    /** All reports filed against the given comment (for the admin moderation view). */
    List<CommentReportEntity> findByIdCommentId(UUID commentId);
}
