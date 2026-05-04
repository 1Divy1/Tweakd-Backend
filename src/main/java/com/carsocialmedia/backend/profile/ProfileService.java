package com.carsocialmedia.backend.profile;

public interface ProfileService {
    ProfileDto getProfile(String userId);
    ProfileDto completeOnboarding(String userId, OnboardingRequest request);
}