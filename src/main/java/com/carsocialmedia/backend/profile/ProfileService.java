package com.carsocialmedia.backend.profile;

import java.util.List;

public interface ProfileService {
    ProfileDto getProfile(String userId);
    ProfileDto completeOnboarding(String userId, OnboardingRequest request);
    PublicProfileDto getPublicProfileByUsername(String username);
    List<ProfileSearchResultDto> searchByUsername(String prefix);
}