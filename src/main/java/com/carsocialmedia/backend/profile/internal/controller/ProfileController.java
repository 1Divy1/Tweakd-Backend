package com.carsocialmedia.backend.profile.internal.controller;

import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.CarCategoryDto;
import com.carsocialmedia.backend.profile.dto.CategorySelectionRequest;
import com.carsocialmedia.backend.profile.dto.CommunityRoleDto;
import com.carsocialmedia.backend.profile.dto.LocationRequest;
import com.carsocialmedia.backend.profile.dto.NotificationPreferencesDto;
import com.carsocialmedia.backend.profile.dto.NotificationPreferencesRequest;
import com.carsocialmedia.backend.profile.dto.OnboardingRequest;
import com.carsocialmedia.backend.profile.dto.ProfileDto;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.dto.PublicProfileDto;
import com.carsocialmedia.backend.profile.dto.RealtimeLocationRequest;
import com.carsocialmedia.backend.profile.dto.RoleSelectionRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @PostMapping("/onboarding")
    public ProfileDto completeOnboarding(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OnboardingRequest request
    ) {
        return profileService.completeOnboarding(jwt.getSubject(), request);
    }

    // TODO: remove '/by-username' and just use '/{username}'
    @GetMapping("/by-username/{username}")
    public PublicProfileDto getProfileByUsername(@PathVariable String username) {
        return profileService.getPublicProfileByUsername(username);
    }

    @GetMapping("/exists/{username}")
    public boolean existsByUsername(@PathVariable String username) {
        return profileService.existsByUsername(username);
    }

    @GetMapping("/search")
    public List<ProfileSearchResultDto> searchByUsername(@RequestParam("q") String query) {
        return profileService.searchByUsername(query);
    }

    @GetMapping("/me")
    public ProfileDto getProfile(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getProfile(jwt.getSubject());
    }

    @PatchMapping("/me/location")
    public ProfileDto updateLocation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody LocationRequest request
    ) {
        return profileService.updateLocation(jwt.getSubject(), request);
    }

    @PatchMapping("/me/realtime-location")
    public void updateRealtimeLocation(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RealtimeLocationRequest request
    ) {
        profileService.updateRealtimeLocation(jwt.getSubject(), request);
    }

    @GetMapping("/me/car-categories")
    public List<CarCategoryDto> getCarCategories(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getCarCategories(jwt.getSubject());
    }

    @PutMapping("/me/car-categories")
    public List<CarCategoryDto> setCarCategories(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CategorySelectionRequest request
    ) {
        return profileService.setCarCategories(jwt.getSubject(), request);
    }

    @GetMapping("/me/community-roles")
    public List<CommunityRoleDto> getCommunityRoles(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getCommunityRoles(jwt.getSubject());
    }

    @PutMapping("/me/community-roles")
    public List<CommunityRoleDto> setCommunityRoles(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RoleSelectionRequest request
    ) {
        return profileService.setCommunityRoles(jwt.getSubject(), request);
    }

    @GetMapping("/me/notifications")
    public NotificationPreferencesDto getNotificationPreferences(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getNotificationPreferences(jwt.getSubject());
    }

    @PutMapping("/me/notifications")
    public NotificationPreferencesDto updateNotificationPreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody NotificationPreferencesRequest request
    ) {
        return profileService.updateNotificationPreferences(jwt.getSubject(), request);
    }
}
