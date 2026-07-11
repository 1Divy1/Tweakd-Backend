package com.carsocialmedia.backend.report.internal.repositories;

import com.carsocialmedia.backend.report.internal.entities.ForumThreadReplyReportEntity;
import com.carsocialmedia.backend.report.internal.entities.ForumThreadReplyReportId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ForumThreadReplyReportRepository extends JpaRepository<ForumThreadReplyReportEntity, ForumThreadReplyReportId> {

    boolean existsByIdReplyIdAndIdReporterId(UUID replyId, UUID reporterId);

    /** All forum-reply reports filed by the given reporter (for their "my reports" feed). */
    List<ForumThreadReplyReportEntity> findByIdReporterId(UUID reporterId);

    /** All reports filed against the given reply (for the admin moderation view). */
    List<ForumThreadReplyReportEntity> findByIdReplyId(UUID replyId);
}
