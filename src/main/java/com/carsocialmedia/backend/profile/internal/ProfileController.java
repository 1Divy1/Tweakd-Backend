package com.carsocialmedia.backend.profile.internal;

import com.carsocialmedia.backend.profile.OnboardingRequest;
import com.carsocialmedia.backend.profile.ProfileDto;
import com.carsocialmedia.backend.profile.ProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile")
class ProfileController {
    private final ProfileService profileService;

    ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ProfileDto getProfile(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getProfile(jwt.getSubject());
    }

    @PostMapping("/onboarding")
    public ProfileDto completeOnboarding(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OnboardingRequest request
    ) {
        return profileService.completeOnboarding(jwt.getSubject(), request);
    }
}