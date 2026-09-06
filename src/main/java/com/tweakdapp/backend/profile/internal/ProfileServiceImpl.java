package com.tweakdapp.backend.profile.internal;

import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.BadgeTrigger;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.shared.geo.CityDto;
import com.tweakdapp.backend.shared.geo.CountryDto;
import com.tweakdapp.backend.profile.dto.LanguageOptionDto;
import com.tweakdapp.backend.profile.dto.LocationRequest;
import com.tweakdapp.backend.profile.dto.ProfileEditRequest;
import com.tweakdapp.backend.profile.dto.NotificationPreferencesDto;
import com.tweakdapp.backend.profile.dto.NotificationPreferencesRequest;
import com.tweakdapp.backend.profile.dto.OnboardingRequest;
import com.tweakdapp.backend.profile.dto.ProfileDto;
import com.tweakdapp.backend.profile.dto.ProfileModerationSnapshotDto;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.profile.dto.PublicProfileDto;
import com.tweakdapp.backend.profile.dto.RealtimeLocationRequest;
import com.tweakdapp.backend.profile.dto.ReputationAdjustmentDto;
import com.tweakdapp.backend.profile.exception.CannotReportSelfException;
import com.tweakdapp.backend.profile.exception.InvalidReferenceException;
import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.profile.exception.UsernameAlreadyTakenException;
import com.tweakdapp.backend.profile.internal.entity.*;
import com.tweakdapp.backend.profile.internal.repository.*;
import com.tweakdapp.backend.report.ReportService;
import com.tweakdapp.backend.shared.geo.CityEntity;
import com.tweakdapp.backend.shared.geo.CityRepository;
import com.tweakdapp.backend.shared.geo.CountryEntity;
import com.tweakdapp.backend.shared.geo.CountryRepository;
import com.tweakdapp.backend.shared.geo.GeoSupport;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;
    private final CountryRepository countryRepository;
    private final CityRepository cityRepository;
    private final NotificationPreferencesRepository notificationPreferencesRepository;
    private final LanguageOptionRepository languageOptionRepository;
    private final ReportService reportService;
    private final StorageService storageService;
    private final ProfileDtoMapper mapper;
    private final BadgeService badgeService;
    private final BanCache banCache;

    ProfileServiceImpl(ProfileRepository profileRepository,
                       CountryRepository countryRepository,
                       CityRepository cityRepository,
                       NotificationPreferencesRepository notificationPreferencesRepository,
                       LanguageOptionRepository languageOptionRepository,
                       ReportService reportService,
                       StorageService storageService,
                       ProfileDtoMapper mapper,
                       BadgeService badgeService,
                       BanCache banCache) {
        this.profileRepository = profileRepository;
        this.countryRepository = countryRepository;
        this.cityRepository = cityRepository;
        this.notificationPreferencesRepository = notificationPreferencesRepository;
        this.languageOptionRepository = languageOptionRepository;
        this.reportService = reportService;
        this.storageService = storageService;
        this.mapper = mapper;
        this.banCache = banCache;
        this.badgeService = badgeService;
    }

    /**
     * Every {@link ProfileDto} / {@link PublicProfileDto} carries its owner's badges, so the profile
     * screen renders its badge row without a second request. One extra indexed read per profile
     * response — cheap, and it keeps the field always present rather than populated on some paths
     * and empty on others.
     *
     * <p>The arrow points this way on purpose. {@code badges} reads nothing from {@code profiles},
     * which is what leaves {@code profile} free to depend on it; the reverse would be a cycle.
     */
    private List<com.tweakdapp.backend.badges.dto.UserBadgeDto> badgesOf(UUID userId) {
        return badgeService.listUserBadges(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileDto getProfile(String userId) {
        ProfileEntity profile = profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId));
        return mapper.toDto(profile, badgesOf(profile.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return profileRepository.existsByUsername(username);
    }

    @Override
    @Transactional
    public ProfileDto completeOnboarding(String userId, OnboardingRequest request) {

        UUID id = UUID.fromString(userId);
        ProfileEntity profile = profileRepository
                .findById(id)
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId));

        if (profileRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyTakenException(request.username());
        }

        profile.setName(request.name());
        profile.setUsername(request.username());
        profile.setBio(request.bio());
        profile.setRequiresOnboarding(false);
        applyLocation(profile, request.cityId(), request.discoveryRadiusKm());

        try {
            profileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            throw new UsernameAlreadyTakenException(request.username());
        }

        ensureDefaultNotificationPreferences(id);

        // A new member exists as of now, so whatever badges signing up is currently worth are
        // unlocked here — `pioneer` today, whatever the catalogue says tomorrow. This module names
        // no badge on purpose: it reports the event, and `badges` decides what it is worth, so
        // adding or withdrawing a signup badge never touches this file.
        //
        // Onboarding rather than signup because signup happens in Supabase — the handle_new_user
        // trigger inserts the profile and the backend never sees it. It is also the better moment:
        // an account that never chose a username is not a member yet.
        //
        // Judged against the account's creation date, not now. A limited-time badge is a statement
        // about when someone joined, so signing up two days before a cutoff and finishing onboarding
        // a week later still earns it — and signing up after the cutoff does not earn it by
        // onboarding quickly.
        //
        // In this transaction deliberately: if onboarding rolls back there is no member and there
        // should be no badge. Idempotent, so a retried or repeated onboarding awards nothing twice,
        // and the return value is ignored because the app collects new unlocks from its own
        // pending-celebration list on the next launch.
        badgeService.awardForTrigger(id, BadgeTrigger.ACCOUNT_CREATED, profile.getCreatedAt());

        return mapper.toDto(profile, badgesOf(profile.getId()));
    }

    @Override
    @Transactional
    public ProfileDto updateProfile(String userId, ProfileEditRequest request) {
        ProfileEntity profile = profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId));
        if (request.name() != null) {
            profile.setName(request.name());
        }
        if (request.bio() != null) {
            profile.setBio(request.bio());
        }
        profileRepository.save(profile);
        return mapper.toDto(profile, badgesOf(profile.getId()));
    }

    @Override
    @Transactional
    public ProfileDto updateAvatar(String userId, String key) {
        // Upload URLs namespace keys per user; accepting anything else would let a caller point
        // their profile at (and later after-commit-delete) another user's object.
        if (!key.startsWith("avatars/" + userId + "/")) {
            throw new InvalidReferenceException("Avatar key does not belong to the caller");
        }
        ProfileEntity profile = profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId));
        String previous = profile.getAvatarUrl();
        profile.setAvatarUrl(key);
        profileRepository.save(profile);
        deleteAvatarAfterCommit(previous);
        return mapper.toDto(profile, badgesOf(profile.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProfileDto getPublicProfileByUsername(String username) {
        ProfileEntity profile = profileRepository
                .findByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));
        return mapper.toPublicDto(profile, badgesOf(profile.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PublicProfileDto> findPublicProfileById(UUID profileId) {
        return profileRepository.findById(profileId)
                .map(profile -> mapper.toPublicDto(profile, badgesOf(profile.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBanned(UUID profileId) {
        // Through the same cache the interceptor uses, so an unauthenticated public read costs no
        // extra query on the 2-connection pool and a moderator's ban takes effect at the same speed
        // everywhere.
        return banCache.isBanned(profileId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.tweakdapp.backend.badges.dto.UserBadgeDto> getBadgesByUsername(String username) {
        return badgesOf(profileRepository
                .findByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username))
                .getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileSearchResultDto> searchByUsername(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return profileRepository
                .findTop20ByUsernameStartingWithIgnoreCaseOrderByUsernameAsc(prefix)
                .stream()
                .map(mapper::toSearchResultDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findIdByUsername(String username) {
        return profileRepository.findByUsername(username).map(ProfileEntity::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileSearchResultDto> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return profileRepository.findAllByIdIn(ids)
                .stream()
                .map(mapper::toSearchResultDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findBusinessProfileIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return profileRepository.findBusinessIds(ids);
    }

    // ---- moderation ----------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfileModerationSnapshotDto> findModerationSnapshot(UUID profileId) {
        return profileRepository.findById(profileId)
                .map(profile -> new ProfileModerationSnapshotDto(
                        profile.getId(),
                        profile.getUsername(),
                        profile.getName(),
                        mapper.resolveAvatarUrl(profile.getAvatarUrl()),
                        profile.getFollowersCount(),
                        profile.isBusiness(),
                        profile.isBanned(),
                        profile.getBannedUntil(),
                        profile.getCreatedAt()));
    }

    @Override
    @Transactional
    public void banUser(UUID profileId, Instant until) {
        ProfileEntity profile = profileRepository.findById(profileId)
                .orElseThrow(() -> ProfileNotFoundException.byUserId(profileId.toString()));
        profile.setBanned(true);
        profile.setBannedUntil(until);
        banCache.evict(profileId);
    }

    @Override
    @Transactional
    public void unbanUser(UUID profileId) {
        ProfileEntity profile = profileRepository.findById(profileId)
                .orElseThrow(() -> ProfileNotFoundException.byUserId(profileId.toString()));
        profile.setBanned(false);
        profile.setBannedUntil(null);
        banCache.evict(profileId);
    }

    // ---- reputation ---------------------------------------------------------

    @Override
    @Transactional
    public ReputationAdjustmentDto applyReputationDelta(UUID userId, int delta) {
        ProfileEntity profile = profileRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId.toString()));

        int previousScore = profile.getReputationScore();
        // Clamped, so a run of penalties bottoms out at zero rather than going negative. The
        // history row records the clamped pair, which is why the caller cannot just derive it.
        int newScore = Math.max(0, previousScore + delta);
        profile.setReputationScore(newScore);

        return new ReputationAdjustmentDto(previousScore, newScore);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> findReputationScore(UUID userId) {
        return profileRepository.findReputationScore(userId);
    }

    // ---- onboarding reference data -----------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CountryDto> listCountries() {
        return countryRepository.findAllByOrderByNameAsc()
                .stream().map(CountryEntity::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CityDto> listCities(String countryId) {
        return cityRepository.findByCountry_IdOrderByNameAsc(countryId)
                .stream().map(CityEntity::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LanguageOptionDto> listLanguageOptions() {
        return languageOptionRepository.findAllByOrderByLanguageAsc()
                .stream().map(LanguageOptionEntity::toDto).toList();
    }

    @Override
    @Transactional
    public ProfileDto updateLanguage(String userId, String languageId) {
        ProfileEntity profile = profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId));
        if (!languageOptionRepository.existsById(languageId)) {
            throw new InvalidReferenceException("Unknown language: " + languageId);
        }
        profile.setAppLanguage(languageId);
        profileRepository.save(profile);
        return mapper.toDto(profile, badgesOf(profile.getId()));
    }

    // ---- location ----------------------------------------------------------

    @Override
    @Transactional
    public ProfileDto updateLocation(String userId, LocationRequest request) {
        ProfileEntity profile = profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId));
        applyLocation(profile, request.cityId(), request.discoveryRadiusKm());
        profileRepository.save(profile);
        return mapper.toDto(profile, badgesOf(profile.getId()));
    }

    @Override
    @Transactional
    public void updateRealtimeLocation(String userId, RealtimeLocationRequest request) {
        ProfileEntity profile = profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId));
        profile.setRealtimeLocation(GeoSupport.point(request.lat(), request.lng()));
        profileRepository.save(profile);
    }

    // ---- notification preferences ------------------------------------------

    @Override
    @Transactional
    public NotificationPreferencesDto getNotificationPreferences(String userId) {
        UUID id = UUID.fromString(userId);
        return notificationPreferencesRepository.findById(id)
                .orElseGet(() -> {
                    requireProfile(id, userId);
                    return notificationPreferencesRepository.save(defaultPreferences(id));
                })
                .toDto();
    }

    @Override
    @Transactional
    public NotificationPreferencesDto updateNotificationPreferences(String userId, NotificationPreferencesRequest request) {
        UUID id = UUID.fromString(userId);
        NotificationPreferencesEntity prefs = notificationPreferencesRepository.findById(id)
                .orElseGet(() -> {
                    requireProfile(id, userId);
                    return defaultPreferences(id);
                });
        prefs.setLikesEnabled(request.likesEnabled());
        prefs.setCommentsEnabled(request.commentsEnabled());
        prefs.setSharesEnabled(request.sharesEnabled());
        prefs.setDmsEnabled(request.dmsEnabled());
        prefs.setFlashMeetsEnabled(request.flashMeetsEnabled());
        prefs.setOrganizedEventsEnabled(request.organizedEventsEnabled());
        prefs.setServiceRemindersEnabled(request.serviceRemindersEnabled());
        prefs.setTagsEnabled(request.tagsEnabled());
        prefs.setEventOrganizerEnabled(request.eventOrganizerEnabled());
        prefs.setUpdatedAt(Instant.now());
        return notificationPreferencesRepository.save(prefs).toDto();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferencesDto getNotificationPreferencesOrDefault(UUID userId) {
        // Read-only, no lazy row creation and no 404: a missing preferences row — or a missing
        // profile — reads as all-enabled (the module default), so an async notification listener
        // never fails on a vanished recipient.
        return notificationPreferencesRepository.findById(userId)
                .map(NotificationPreferencesEntity::toDto)
                .orElseGet(() -> new NotificationPreferencesDto(true, true, true, true, true, true, true, true, true));
    }

    // ---- reporting ---------------------------------------------------------

    @Override
    @Transactional
    public void reportProfile(String currentUserId, String username, UUID reasonId) {
        UUID reporterId = UUID.fromString(currentUserId);
        UUID reportedId = profileRepository.findByUsername(username)
                .map(ProfileEntity::getId)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));
        if (reportedId.equals(reporterId)) {
            throw new CannotReportSelfException();
        }
        reportService.reportProfile(reporterId, reportedId, reasonId);
    }

    // ---- helpers -----------------------------------------------------------

    private void applyLocation(ProfileEntity profile, String cityId, Integer radius) {
        if (cityId != null) {
            CityEntity city = cityRepository
                    .findById(cityId)
                    .orElseThrow(() -> new InvalidReferenceException("Unknown city: " + cityId));
            profile.setCity(city);
        }
        if (radius != null) {
            profile.setDiscoveryRadiusKm(radius);
        }
    }

    private void ensureDefaultNotificationPreferences(UUID profileId) {
        if (!notificationPreferencesRepository.existsById(profileId)) {
            notificationPreferencesRepository.save(defaultPreferences(profileId));
        }
    }

    private NotificationPreferencesEntity defaultPreferences(UUID profileId) {
        NotificationPreferencesEntity prefs = new NotificationPreferencesEntity();
        prefs.setProfileId(profileId);
        prefs.setUpdatedAt(Instant.now());
        return prefs;
    }

    /**
     * If {@code previous} was an R2 object key (non-blank and not a legacy {@code http} URL), registers
     * an after-commit callback to delete it from R2. Mirrors the posts module: deletion runs only if the
     * DB transaction commits, and a failed R2 delete just leaves an orphaned object (DB stays consistent).
     */
    private void deleteAvatarAfterCommit(String previous) {
        if (previous == null || previous.isBlank() || previous.startsWith("http")) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storageService.deleteByKeys(StorageBucket.AVATARS, List.of(previous));
            }
        });
    }

    private void requireProfile(UUID id, String userId) {
        if (!profileRepository.existsById(id)) {
            throw ProfileNotFoundException.byUserId(userId);
        }
    }
}