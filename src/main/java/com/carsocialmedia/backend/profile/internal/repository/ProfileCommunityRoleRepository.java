package com.carsocialmedia.backend.profile.internal.repository;

import com.carsocialmedia.backend.profile.internal.entity.ProfileCommunityRoleId;
import com.carsocialmedia.backend.profile.internal.entity.ProfileCommunityRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Join rows linking profiles to community roles
 * ({@code profile_community_roles_junction}). Written replace-all per profile.
 */
public interface ProfileCommunityRoleRepository extends JpaRepository<ProfileCommunityRoleEntity, ProfileCommunityRoleId> {
    List<ProfileCommunityRoleEntity> findByIdProfileId(UUID profileId);

    void deleteByIdProfileId(UUID profileId);
}