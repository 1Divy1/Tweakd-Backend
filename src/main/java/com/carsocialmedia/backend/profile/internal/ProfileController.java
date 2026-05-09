package com.carsocialmedia.backend.profile.internal;

import com.carsocialmedia.backend.profile.OnboardingRequest;
import com.carsocialmedia.backend.profile.PrivacyRequest;
import com.carsocialmedia.backend.profile.ProfileDto;
import com.carsocialmedia.backend.profile.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.PublicProfileDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @PatchMapping("/me/privacy")
    public ProfileDto setPrivacy(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PrivacyRequest request
    ) {
        return profileService.setPrivacy(jwt.getSubject(), request.isPrivate());
    }

    @GetMapping("/by-username/{username}")
    public PublicProfileDto getProfileByUsername(@PathVariable String username) {
        return profileService.getPublicProfileByUsername(username);
    }

    @GetMapping("/search")
    public List<ProfileSearchResultDto> searchByUsername(@RequestParam("q") String query) {
        return profileService.searchByUsername(query);
    }
}
