package com.carsocialmedia.backend.profile.internal;

import com.carsocialmedia.backend.profile.ProfileService;
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
import com.carsocialmedia.backend.profile.exception.InvalidReferenceException;
import com.carsocialmedia.backend.profile.exception.ProfileNotFoundException;
import com.carsocialmedia.backend.profile.exception.UsernameAlreadyTakenException;
import com.carsocialmedia.backend.profile.internal.entity.*;
import com.carsocialmedia.backend.profile.internal.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    ProfileServiceImpl(ProfileRepository profileRepository,
                       CountryRepository countryRepository,
                       CityRepository cityRepository,
                       CommunityRoleOptionRepository communityRoleOptionRepository,
                       CarCategoryOptionRepository carCategoryOptionRepository,
                       NotificationPreferencesRepository notificationPreferencesRepository,
                       ProfileCarCategoryRepository profileCarCategoryRepository,
                       ProfileCommunityRoleRepository profileCommunityRoleRepository) {
        this.profileRepository = profileRepository;
        this.countryRepository = countryRepository;
        this.cityRepository = cityRepository;
        this.communityRoleOptionRepository = communityRoleOptionRepository;
        this.carCategoryOptionRepository = carCategoryOptionRepository;
        this.notificationPreferencesRepository = notificationPreferencesRepository;
        this.profileCarCategoryRepository = profileCarCategoryRepository;
        this.profileCommunityRoleRepository = profileCommunityRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileDto getProfile(String userId) {
        return profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId))
                .toDto();
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

        return profile.toDto();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProfileDto getPublicProfileByUsername(String username) {
        return profileRepository
                .findByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username))
                .toPublicDto();
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
                .map(ProfileEntity::toSearchResultDto)
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
                .map(ProfileEntity::toSearchResultDto)
                .toList();
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

    // ---- location ----------------------------------------------------------

    @Override
    @Transactional
    public ProfileDto updateLocation(String userId, LocationRequest request) {
        ProfileEntity profile = profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId));
        applyLocation(profile, request.cityId(), request.discoveryRadiusKm());
        profileRepository.save(profile);
        return profile.toDto();
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

    private void requireProfile(UUID id, String userId) {
        if (!profileRepository.existsById(id)) {
            throw ProfileNotFoundException.byUserId(userId);
        }
    }
}