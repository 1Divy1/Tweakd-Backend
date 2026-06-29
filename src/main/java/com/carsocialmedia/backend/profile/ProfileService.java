package com.carsocialmedia.backend.profile;

import com.carsocialmedia.backend.profile.dto.CarCategoryDto;
import com.carsocialmedia.backend.profile.dto.CategorySelectionRequest;
import com.carsocialmedia.backend.profile.dto.CityDto;
import com.carsocialmedia.backend.profile.dto.CommunityRoleDto;
import com.carsocialmedia.backend.profile.dto.CountryDto;
import com.carsocialmedia.backend.profile.dto.LocationRequest;
import com.carsocialmedia.backend.profile.dto.NotificationPreferencesDto;
import com.carsocialmedia.backend.profile.dto.NotificationPreferencesRequest;
import com.carsocialmedia.backend.profile.dto.OnboardingRequest;
import com.carsocialmedia.backend.profile.dto.ProfileDto;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.dto.PublicProfileDto;
import com.carsocialmedia.backend.profile.dto.RealtimeLocationRequest;
import com.carsocialmedia.backend.profile.dto.RoleSelectionRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileService {

    /**
     * Lookup helpers for sibling modules. Returning DTOs (not entities) keeps
     * profile internals private while still letting other modules resolve
     * usernames or hydrate user lists.
     */
    Optional<UUID> findIdByUsername(String username);
    boolean existsByUsername(String username);
    List<ProfileSearchResultDto> findByIds(Collection<UUID> ids);

    // TODO: Add docs for the below methods.

    ProfileDto getProfile(String userId);

    ProfileDto completeOnboarding(String userId, OnboardingRequest request);

    PublicProfileDto getPublicProfileByUsername(String username);

    List<ProfileSearchResultDto> searchByUsername(String prefix);

    // ---- onboarding reference data (read-only lookups) ---------------------

    List<CountryDto> listCountries();

    List<CityDto> listCities(String countryId);

    List<CommunityRoleDto> listCommunityRoles();

    List<CarCategoryDto> listCarCategories();

    // ---- location ----------------------------------------------------------

    /** Updates the user's city, discovery radius, and/or home-location point. */
    ProfileDto updateLocation(String userId, LocationRequest request);

    /** Stores the user's live location after they opt in to realtime location. */
    void updateRealtimeLocation(String userId, RealtimeLocationRequest request);

    // ---- favorite car categories (replace-all) -----------------------------

    List<CarCategoryDto> getCarCategories(String userId);

    List<CarCategoryDto> setCarCategories(String userId, CategorySelectionRequest request);

    // ---- community roles (replace-all) -------------------------------------

    List<CommunityRoleDto> getCommunityRoles(String userId);

    List<CommunityRoleDto> setCommunityRoles(String userId, RoleSelectionRequest request);

    // ---- notification preferences ------------------------------------------

    NotificationPreferencesDto getNotificationPreferences(String userId);

    NotificationPreferencesDto updateNotificationPreferences(String userId, NotificationPreferencesRequest request);
}
