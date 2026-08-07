package com.carsocialmedia.backend.profile;

import com.carsocialmedia.backend.profile.dto.CarCategoryDto;
import com.carsocialmedia.backend.profile.dto.CategorySelectionRequest;
import com.carsocialmedia.backend.shared.geo.CityDto;
import com.carsocialmedia.backend.profile.dto.CommunityRoleDto;
import com.carsocialmedia.backend.shared.geo.CountryDto;
import com.carsocialmedia.backend.profile.dto.LanguageOptionDto;
import com.carsocialmedia.backend.profile.dto.LocationRequest;
import com.carsocialmedia.backend.profile.dto.ProfileEditRequest;
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

    /** Which of the given profile ids are business accounts (e.g. for support-ticket badges). */
    List<UUID> findBusinessProfileIds(Collection<UUID> ids);

    // ---- moderation (called by the admin module; no auth logic here) --------

    /**
     * The author panel of a moderation case (identity, reach, account age, ban state), or empty if
     * the profile is gone. Empty rather than an exception on purpose: the caller runs inside its
     * own transaction, and a not-found thrown across that boundary would mark it rollback-only.
     */
    Optional<com.carsocialmedia.backend.profile.dto.ProfileModerationSnapshotDto> findModerationSnapshot(UUID profileId);

    /**
     * Bans a user. Enforced by the profile module's request interceptor, which rejects every
     * request from a banned user with 403 (subject to a short cache, so enforcement starts within
     * ~a minute).
     *
     * @param until temp-ban expiry; {@code null} = permanent
     * @throws com.carsocialmedia.backend.profile.exception.ProfileNotFoundException if no such profile
     */
    void banUser(UUID profileId, java.time.Instant until);

    /**
     * Lifts a ban.
     *
     * @throws com.carsocialmedia.backend.profile.exception.ProfileNotFoundException if no such profile
     */
    void unbanUser(UUID profileId);

    // TODO: Add docs for the below methods.

    ProfileDto getProfile(String userId);

    ProfileDto completeOnboarding(String userId, OnboardingRequest request);

    /** Partial edit of the caller's own name/bio (absent fields left unchanged). Returns the updated profile. */
    ProfileDto updateProfile(String userId, ProfileEditRequest request);

    /**
     * Persists a new avatar R2 object key to the caller's profile and deletes the previously stored
     * R2 object (if any) after commit. Returns the updated profile.
     */
    ProfileDto updateAvatar(String userId, String key);

    PublicProfileDto getPublicProfileByUsername(String username);

    List<ProfileSearchResultDto> searchByUsername(String prefix);

    // ---- onboarding reference data (read-only lookups) ---------------------

    List<CountryDto> listCountries();

    List<CityDto> listCities(String countryId);

    List<CommunityRoleDto> listCommunityRoles();

    List<CarCategoryDto> listCarCategories();

    /** All selectable UI languages (reference data). */
    List<LanguageOptionDto> listLanguageOptions();

    /**
     * Sets the caller's UI language.
     *
     * @param languageId a code from {@code app_language_options} (e.g. {@code "en"}, {@code "ro"})
     * @throws com.carsocialmedia.backend.profile.exception.InvalidReferenceException if the code is unknown
     * @throws com.carsocialmedia.backend.profile.exception.ProfileNotFoundException if no such profile
     */
    ProfileDto updateLanguage(String userId, String languageId);

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

    /**
     * The given user's notification toggles, for gating outbound in-app notifications. Read-only and
     * side-effect-free — unlike {@link #getNotificationPreferences(String)} it neither lazily creates
     * the preferences row nor throws when the profile is gone: a missing preferences row (or a missing
     * profile) reads as all-enabled, matching the module's default treatment. Safe to call from an
     * asynchronous notification listener.
     */
    NotificationPreferencesDto getNotificationPreferencesOrDefault(UUID userId);

    // ---- reporting ---------------------------------------------------------

    /**
     * Files a report against the profile identified by {@code username}, on behalf of the current
     * user. Resolves the username and blocks self-reports here (the profile-side checks); the report
     * row itself is persisted by the {@code report} module.
     *
     * @param currentUserId the reporting user's UUID from the JWT subject
     * @param username the reported user's username
     * @param reasonId an optional preset reason (a {@code profile}-scoped {@code report_reasons} id), or {@code null}
     * @throws com.carsocialmedia.backend.profile.exception.ProfileNotFoundException if the username does not resolve
     * @throws com.carsocialmedia.backend.profile.exception.CannotReportSelfException if the caller reports their own profile
     * @throws com.carsocialmedia.backend.report.exception.InvalidReportReasonException if {@code reasonId}
     *         is given but is not a valid {@code profile} reason
     * @throws com.carsocialmedia.backend.report.exception.DuplicateReportException if the caller already
     *         reported this profile
     */
    void reportProfile(String currentUserId, String username, UUID reasonId);
}
