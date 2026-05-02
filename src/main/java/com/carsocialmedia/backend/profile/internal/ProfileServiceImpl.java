package com.carsocialmedia.backend.profile.internal;

import com.carsocialmedia.backend.profile.ProfileDto;
import com.carsocialmedia.backend.profile.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class ProfileServiceImpl implements ProfileService {

    final private ProfileRepository profileRepository;

    ProfileServiceImpl(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileDto getProfile(String userId) {
        ProfileEntity profile = profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        return new ProfileDto(
                profile.getId(), profile.getRole(), profile.getName(),
                profile.getUsername(), profile.getAvatarUrl(), profile.getBio(),
                profile.getExternalLink(), profile.getFollowersCount(), profile.getFollowingCount(),
                profile.isVerified(), profile.isBusiness(), profile.isRequiresOnboarding()
        );
    }
}
