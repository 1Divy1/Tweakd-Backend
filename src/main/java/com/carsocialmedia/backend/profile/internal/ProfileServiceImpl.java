package com.carsocialmedia.backend.profile.internal;

import com.carsocialmedia.backend.profile.OnboardingRequest;
import com.carsocialmedia.backend.profile.ProfileDto;
import com.carsocialmedia.backend.profile.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.PublicProfileDto;
import com.carsocialmedia.backend.profile.exception.ProfileNotFoundException;
import com.carsocialmedia.backend.profile.exception.UsernameAlreadyTakenException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId))
                .toDto();
    }

    @Override
    @Transactional
    public ProfileDto completeOnboarding(String userId, OnboardingRequest request) {
        ProfileEntity profile = profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId));

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

    @Override
    @Transactional(readOnly = true)
    public PublicProfileDto getPublicProfileByUsername(String username) {
        return profileRepository
                .findByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username))
                .toPublicDto();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileSearchResultDto> searchByUsername(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return profileRepository
                .findTop20ByUsernameStartingWithIgnoreCaseOrderByUsernameAsc(prefix)
                .stream()
                .map(ProfileEntity::toSearchResultDto)
                .toList();
    }
}