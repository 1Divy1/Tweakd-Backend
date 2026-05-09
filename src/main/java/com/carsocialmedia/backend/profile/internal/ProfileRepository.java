package com.carsocialmedia.backend.profile.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {
    boolean existsByUsername(String username);
    Optional<ProfileEntity> findByUsername(String username);
    List<ProfileEntity> findTop20ByUsernameStartingWithIgnoreCaseOrderByUsernameAsc(String prefix);
    List<ProfileEntity> findAllByIdIn(Collection<UUID> ids);
}
