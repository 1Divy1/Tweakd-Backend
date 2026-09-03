package com.tweakdapp.backend.reputation.internal.repository;

import com.tweakdapp.backend.reputation.internal.entity.ReputationReasonEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReputationReasonRepository extends JpaRepository<ReputationReasonEntity, String> {

    /**
     * The awardable catalogue, grouped for display. Retired reasons are excluded — they still
     * resolve by id for reading old history, but must not appear in the "how to earn reputation"
     * sheet as if they were still on offer.
     */
    List<ReputationReasonEntity> findByActiveTrueOrderByCategoryAscPointsDesc();

    /** A reason that may still be awarded. An inactive one reads as absent on the write path. */
    Optional<ReputationReasonEntity> findByIdAndActiveTrue(String id);
}
