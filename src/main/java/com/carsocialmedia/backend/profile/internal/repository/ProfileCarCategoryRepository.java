package com.carsocialmedia.backend.profile.internal.repository;

import com.carsocialmedia.backend.profile.internal.entity.ProfileCarCategoryId;
import com.carsocialmedia.backend.profile.internal.entity.ProfileCarCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Join rows linking profiles to favorite car categories
 * ({@code profile_car_categories_junction}). Written replace-all per profile.
 */
public interface ProfileCarCategoryRepository extends JpaRepository<ProfileCarCategoryEntity, ProfileCarCategoryId> {
    List<ProfileCarCategoryEntity> findByIdProfileId(UUID profileId);

    void deleteByIdProfileId(UUID profileId);
}