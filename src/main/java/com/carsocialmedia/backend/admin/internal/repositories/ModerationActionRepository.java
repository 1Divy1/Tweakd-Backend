package com.carsocialmedia.backend.admin.internal.repositories;

import com.carsocialmedia.backend.admin.internal.entities.ModerationActionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModerationActionRepository extends JpaRepository<ModerationActionEntity, UUID> {

    /** A case's action history, oldest first (reads as a timeline in the case detail). */
    List<ModerationActionEntity> findByCaseIdOrderByCreatedAtAsc(Long caseId);
}
