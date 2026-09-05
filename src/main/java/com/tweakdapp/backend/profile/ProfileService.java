package com.tweakdapp.backend.profile;

import com.tweakdapp.backend.shared.geo.CityDto;
import com.tweakdapp.backend.shared.geo.CountryDto;
import com.tweakdapp.backend.profile.dto.LanguageOptionDto;
import com.tweakdapp.backend.profile.dto.LocationRequest;
import com.tweakdapp.backend.profile.dto.ProfileEditRequest;
import com.tweakdapp.backend.profile.dto.NotificationPreferencesDto;
import com.tweakdapp.backend.profile.dto.NotificationPreferencesRequest;
import com.tweakdapp.backend.profile.dto.OnboardingRequest;
import com.tweakdapp.backend.profile.dto.ProfileDto;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.profile.dto.PublicProfileDto;
import com.tweakdapp.backend.profile.dto.RealtimeLocationRequest;

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

    /**
     * The same profile {@link #getPublicProfileByUsername} returns, addressed by id.
     *
     * <p>For callers that already hold an owner's UUID and would otherwise have to resolve a
     * username first only to look it back up — the {@code garage} module building the owner card on
     * a public car page, for one. Empty rather than throwing: the caller's own row exists, so a
     * missing profile here is a race with a deleted account, not a 404 they should surface.
     */
    Optional<PublicProfileDto> findPublicProfileById(UUID profileId);

    /**
     * Whether this profile is currently banned (a temporary ban whose {@code banned_until} has
     * passed reads as not banned).
     *
     * <p>{@code BannedUserInterceptor} covers every authenticated request, so this exists for the
     * paths it cannot see: unauthenticated ones. A banned user's public share links must stop
     * serving, and nothing else would notice.
     */
    boolean isBanned(UUID profileId);

    // ---- moderation (called by the admin module; no auth logic here) --------

    /**
     * The author panel of a moderation case (identity, reach, account age, ban state), or empty if
     * the profile is gone. Empty rather than an exception on purpose: the caller runs inside its
     * own transaction, and a not-found thrown across that boundary would mark it rollback-only.
     */
    Optional<com.tweakdapp.backend.profile.dto.ProfileModerationSnapshotDto> findModerationSnapshot(UUID profileId);

    /**
     * Bans a user. Enforced by the profile module's request interceptor, which rejects every
     * request from a banned user with 403 (subject to a short cache, so enforcement starts within
     * ~a minute).
     *
     * @param until temp-ban expiry; {@code null} = permanent
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if no such profile
     */
    void banUser(UUID profileId, java.time.Instant until);

    /**
     * Lifts a ban.
     *
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if no such profile
     */
    void unbanUser(UUID profileId);

    // ---- reputation (called by the reputation module) ----------------------

    /**
     * Moves a profile's {@code reputation_score} by {@code delta} and reports where it started and
     * ended. Negative deltas are allowed (moderation penalties); the resulting score is clamped at
     * zero, so a heavily penalised account bottoms out rather than going negative.
     *
     * <p>Reputation lives on {@code profiles}, which this module owns, but the reason catalogue and
     * the per-achievement history belong to the {@code reputation} module — so this is the seam
     * between them. The row is read under a pessimistic write lock and must be called from inside
     * the caller's transaction: the history row and the score have to move together or not at all.
     *
     * @return the before/after pair to record on the history row
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if no such profile
     */
    com.tweakdapp.backend.profile.dto.ReputationAdjustmentDto applyReputationDelta(UUID userId, int delta);

    /**
     * The profile's current {@code reputation_score}, or empty if there is no such profile.
     *
     * <p>The authoritative read for the {@code reputation} module's profile block: that module owns
     * the itemised history, this one owns the total, and the two are kept from drifting by nobody
     * ever re-deriving one from the other.
     */
    Optional<Integer> findReputationScore(UUID userId);

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

    /**
     * Just the badges on a profile, by username — the same list
     * {@link PublicProfileDto#badges()} already carries.
     *
     * <p>It exists as its own read so the client can refresh the badge row on its own after an
     * unlock animation, without re-pulling the profile. The profile response stays the primary
     * path; this is the cheap follow-up.
     *
     * <p>Lives here rather than in {@code badges} because resolving a username to an id is this
     * module's job, and {@code badges} deliberately reads nothing from {@code profiles} — that is
     * what keeps the dependency one-way.
     *
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if the username does not resolve
     */
    List<com.tweakdapp.backend.badges.dto.UserBadgeDto> getBadgesByUsername(String username);

    List<ProfileSearchResultDto> searchByUsername(String prefix);

    // ---- onboarding reference data (read-only lookups) ---------------------

    List<CountryDto> listCountries();

    List<CityDto> listCities(String countryId);

    /** All selectable UI languages (reference data). */
    List<LanguageOptionDto> listLanguageOptions();

    /**
     * Sets the caller's UI language.
     *
     * @param languageId a code from {@code app_language_options} (e.g. {@code "en"}, {@code "ro"})
     * @throws com.tweakdapp.backend.profile.exception.InvalidReferenceException if the code is unknown
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if no such profile
     */
    ProfileDto updateLanguage(String userId, String languageId);

    // ---- location ----------------------------------------------------------

    /** Updates the user's city, discovery radius, and/or home-location point. */
    ProfileDto updateLocation(String userId, LocationRequest request);

    /** Stores the user's live location after they opt in to realtime location. */
    void updateRealtimeLocation(String userId, RealtimeLocationRequest request);

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
     * @throws com.tweakdapp.backend.profile.exception.ProfileNotFoundException if the username does not resolve
     * @throws com.tweakdapp.backend.profile.exception.CannotReportSelfException if the caller reports their own profile
     * @throws com.tweakdapp.backend.report.exception.InvalidReportReasonException if {@code reasonId}
     *         is given but is not a valid {@code profile} reason
     * @throws com.tweakdapp.backend.report.exception.DuplicateReportException if the caller already
     *         reported this profile
     */
    void reportProfile(String currentUserId, String username, UUID reasonId);
}
