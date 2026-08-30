package com.tweakdapp.backend.report.internal.repositories;

import com.tweakdapp.backend.report.internal.entities.ForumThreadReportEntity;
import com.tweakdapp.backend.report.internal.entities.ForumThreadReportId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ForumThreadReportRepository extends JpaRepository<ForumThreadReportEntity, ForumThreadReportId> {

    boolean existsByIdThreadIdAndIdReporterId(UUID threadId, UUID reporterId);

    /** All forum-thread reports filed by the given reporter (for their "my reports" feed). */
    List<ForumThreadReportEntity> findByIdReporterId(UUID reporterId);

    /** All reports filed against the given thread (for the admin moderation view). */
    List<ForumThreadReportEntity> findByIdThreadId(UUID threadId);
}
