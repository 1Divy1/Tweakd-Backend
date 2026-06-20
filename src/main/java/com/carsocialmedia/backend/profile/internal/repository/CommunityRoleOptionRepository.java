package com.carsocialmedia.backend.profile.internal.repository;

import com.carsocialmedia.backend.profile.internal.entity.CommunityRoleOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Read-only reference data for {@code community_role_options}.
 */
public interface CommunityRoleOptionRepository extends JpaRepository<CommunityRoleOptionEntity, String> {
    List<CommunityRoleOptionEntity> findAllByOrderByNameAsc();

    /**
     * Counts how many of the given IDs exist. Used to validate a whole selection in one
     * query: if the count differs from the number of distinct requested IDs, at least one
     * is invalid (the service then rejects with {@code InvalidReferenceException}).
     */
    long countByIdIn(Collection<String> ids);
}