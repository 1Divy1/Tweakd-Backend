package com.carsocialmedia.backend.profile.internal;

import com.carsocialmedia.backend.profile.OnboardingRequest;
import com.carsocialmedia.backend.profile.ProfileDto;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.exception.ProfileNotFoundException;
import com.carsocialmedia.backend.profile.exception.UsernameAlreadyTakenException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;

    ProfileServiceImpl(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileDto getProfile(String userId) {
        return profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> new ProfileNotFoundException(userId))
                .toDto();
    }

    @Override
    @Transactional
    public ProfileDto completeOnboarding(String userId, OnboardingRequest request) {
        ProfileEntity profile = profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> new ProfileNotFoundException(userId));

        if (profileRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyTakenException(request.username());
        }

        profile.setUsername(request.username());
        profile.setBio(request.bio());
        profile.setRequiresOnboarding(false);

        try {
            profileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            throw new UsernameAlreadyTakenException(request.username());
        }

        return profile.toDto();
    }
}