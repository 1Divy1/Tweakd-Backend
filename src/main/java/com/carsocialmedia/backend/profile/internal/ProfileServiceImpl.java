package com.carsocialmedia.backend.profile.internal;

import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.CarCategoryDto;
import com.carsocialmedia.backend.profile.dto.CategorySelectionRequest;
import com.carsocialmedia.backend.profile.dto.CityDto;
import com.carsocialmedia.backend.profile.dto.CommunityRoleDto;
import com.carsocialmedia.backend.profile.dto.CountryDto;
import com.carsocialmedia.backend.profile.dto.LanguageOptionDto;
import com.carsocialmedia.backend.profile.dto.LocationRequest;
import com.carsocialmedia.backend.profile.dto.ProfileEditRequest;
import com.carsocialmedia.backend.profile.dto.NotificationPreferencesDto;
import com.carsocialmedia.backend.profile.dto.NotificationPreferencesRequest;
import com.carsocialmedia.backend.profile.dto.OnboardingRequest;
import com.carsocialmedia.backend.profile.dto.ProfileDto;
import com.carsocialmedia.backend.profile.dto.ProfileModerationSnapshotDto;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.dto.PublicProfileDto;
import com.carsocialmedia.backend.profile.dto.RealtimeLocationRequest;
import com.carsocialmedia.backend.profile.dto.RoleSelectionRequest;
import com.carsocialmedia.backend.profile.exception.CannotReportSelfException;
import com.carsocialmedia.backend.profile.exception.InvalidReferenceException;
import com.carsocialmedia.backend.profile.exception.ProfileNotFoundException;
import com.carsocialmedia.backend.profile.exception.UsernameAlreadyTakenException;
import com.carsocialmedia.backend.profile.internal.entity.*;
import com.carsocialmedia.backend.profile.internal.repository.*;
import com.carsocialmedia.backend.report.ReportService;
import com.carsocialmedia.backend.storage.StorageBucket;
import com.carsocialmedia.backend.storage.StorageService;
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
    private final CommunityRoleOptionRepository communityRoleOptionRepository;
    private final CarCategoryOptionRepository carCategoryOptionRepository;
    private final NotificationPreferencesRepository notificationPreferencesRepository;
    private final ProfileCarCategoryRepository profileCarCategoryRepository;
    private final ProfileCommunityRoleRepository profileCommunityRoleRepository;
    private final LanguageOptionRepository languageOptionRepository;
    private final ReportService reportService;
    private final StorageService storageService;
    private final ProfileDtoMapper mapper;
    private final BanCache banCache;

    ProfileServiceImpl(ProfileRepository profileRepository,
                       CountryRepository countryRepository,
                       CityRepository cityRepository,
                       CommunityRoleOptionRepository communityRoleOptionRepository,
                       CarCategoryOptionRepository carCategoryOptionRepository,
                       NotificationPreferencesRepository notificationPreferencesRepository,
                       ProfileCarCategoryRepository profileCarCategoryRepository,
                       ProfileCommunityRoleRepository profileCommunityRoleRepository,
                       LanguageOptionRepository languageOptionRepository,
                       ReportService reportService,
                       StorageService storageService,
                       ProfileDtoMapper mapper,
                       BanCache banCache) {
        this.profileRepository = profileRepository;
        this.countryRepository = countryRepository;
        this.cityRepository = cityRepository;
        this.communityRoleOptionRepository = communityRoleOptionRepository;
        this.carCategoryOptionRepository = carCategoryOptionRepository;
        this.notificationPreferencesRepository = notificationPreferencesRepository;
        this.profileCarCategoryRepository = profileCarCategoryRepository;
        this.profileCommunityRoleRepository = profileCommunityRoleRepository;
        this.languageOptionRepository = languageOptionRepository;
        this.reportService = reportService;
        this.storageService = storageService;
        this.mapper = mapper;
        this.banCache = banCache;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileDto getProfile(String userId) {
        return mapper.toDto(profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId)));
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

        profile.setUsername(request.username());
        profile.setBio(request.bio());
        profile.setRequiresOnboarding(false);
        applyLocation(profile, request.cityId(), request.discoveryRadiusKm());

        try {
            profileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            throw new UsernameAlreadyTakenException(request.username());
        }

        replaceCarCategories(id, request.categoryIds());
        replaceCommunityRoles(id, request.roleIds());
        ensureDefaultNotificationPreferences(id);

        return mapper.toDto(profile);
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
        return mapper.toDto(profile);
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
        return mapper.toDto(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProfileDto getPublicProfileByUsername(String username) {
        return mapper.toPublicDto(profileRepository
                .findByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username)));
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
    public List<CommunityRoleDto> listCommunityRoles() {
        return communityRoleOptionRepository.findAllByOrderByNameAsc()
                .stream().map(CommunityRoleOptionEntity::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarCategoryDto> listCarCategories() {
        return carCategoryOptionRepository.findAllByOrderByNameAsc()
                .stream().map(CarCategoryOptionEntity::toDto).toList();
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
        return mapper.toDto(profile);
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
        return mapper.toDto(profile);
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

    // ---- favorite car categories -------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CarCategoryDto> getCarCategories(String userId) {
        List<String> ids = profileCarCategoryRepository.findByIdProfileId(UUID.fromString(userId))
                .stream().map(e -> e.getId().getCategoryId()).toList();
        return carCategoryOptionRepository.findAllById(ids)
                .stream().map(CarCategoryOptionEntity::toDto).toList();
    }

    @Override
    @Transactional
    public List<CarCategoryDto> setCarCategories(String userId, CategorySelectionRequest request) {
        UUID id = UUID.fromString(userId);
        requireProfile(id, userId);
        replaceCarCategories(id, request.categoryIds());
        return getCarCategories(userId);
    }

    // ---- community roles ---------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CommunityRoleDto> getCommunityRoles(String userId) {
        List<String> ids = profileCommunityRoleRepository.findByIdProfileId(UUID.fromString(userId))
                .stream().map(e -> e.getId().getRoleId()).toList();
        return communityRoleOptionRepository.findAllById(ids)
                .stream().map(CommunityRoleOptionEntity::toDto).toList();
    }

    @Override
    @Transactional
    public List<CommunityRoleDto> setCommunityRoles(String userId, RoleSelectionRequest request) {
        UUID id = UUID.fromString(userId);
        requireProfile(id, userId);
        replaceCommunityRoles(id, request.roleIds());
        return getCommunityRoles(userId);
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
        prefs.setPriceDropsEnabled(request.priceDropsEnabled());
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
                .orElseGet(() -> new NotificationPreferencesDto(true, true, true, true, true, true, true));
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

    private void replaceCarCategories(UUID profileId, List<String> categoryIds) {
        List<String> distinct = categoryIds.stream().distinct().toList();
        if (!distinct.isEmpty() && carCategoryOptionRepository.countByIdIn(distinct) != distinct.size()) {
            throw new InvalidReferenceException("One or more car category IDs are invalid");
        }
        profileCarCategoryRepository.deleteByIdProfileId(profileId);
        profileCarCategoryRepository.flush();
        profileCarCategoryRepository.saveAll(
                distinct.stream()
                        .map(cid -> new ProfileCarCategoryEntity(new ProfileCarCategoryId(profileId, cid)))
                        .toList());
    }

    private void replaceCommunityRoles(UUID profileId, List<String> roleIds) {
        List<String> distinct = roleIds.stream().distinct().toList();
        if (!distinct.isEmpty() && communityRoleOptionRepository.countByIdIn(distinct) != distinct.size()) {
            throw new InvalidReferenceException("One or more community role IDs are invalid");
        }
        profileCommunityRoleRepository.deleteByIdProfileId(profileId);
        profileCommunityRoleRepository.flush();
        profileCommunityRoleRepository.saveAll(
                distinct.stream()
                        .map(rid -> new ProfileCommunityRoleEntity(new ProfileCommunityRoleId(profileId, rid)))
                        .toList());
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