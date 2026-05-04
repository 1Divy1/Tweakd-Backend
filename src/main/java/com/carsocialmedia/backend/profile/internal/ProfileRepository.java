package com.carsocialmedia.backend.profile.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {
    boolean existsByUsername(String username);
}