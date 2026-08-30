package com.tweakdapp.backend.profile.internal.controller;

import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.AvatarUpdateRequest;
import com.tweakdapp.backend.profile.dto.LanguageOptionDto;
import com.tweakdapp.backend.profile.dto.LanguageUpdateRequest;
import com.tweakdapp.backend.profile.dto.LocationRequest;
import com.tweakdapp.backend.profile.dto.ProfileEditRequest;
import com.tweakdapp.backend.profile.dto.NotificationPreferencesDto;
import com.tweakdapp.backend.profile.dto.NotificationPreferencesRequest;
import com.tweakdapp.backend.profile.dto.OnboardingRequest;
import com.tweakdapp.backend.profile.dto.ProfileDto;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.profile.dto.PublicProfileDto;
import com.tweakdapp.backend.profile.dto.RealtimeLocationRequest;
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

    @PatchMapping("/me")
    public ProfileDto updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileEditRequest request
    ) {
        return profileService.updateProfile(jwt.getSubject(), request);
    }

    @PatchMapping("/me/avatar")
    public ProfileDto updateAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AvatarUpdateRequest request
    ) {
        return profileService.updateAvatar(jwt.getSubject(), request.key());
    }

    @GetMapping("/language-options")
    public List<LanguageOptionDto> getLanguageOptions() {
        return profileService.listLanguageOptions();
    }

    @PatchMapping("/me/language")
    public ProfileDto updateLanguage(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody LanguageUpdateRequest request
    ) {
        return profileService.updateLanguage(jwt.getSubject(), request.languageId());
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
