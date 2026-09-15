package com.tweakdapp.backend.profile.internal;

import com.tweakdapp.backend.shared.geo.CountryDto;
import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.BadgeTrigger;
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
import com.tweakdapp.backend.profile.exception.CannotReportSelfException;
import com.tweakdapp.backend.profile.exception.InvalidReferenceException;
import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.profile.exception.UsernameAlreadyTakenException;
import com.tweakdapp.backend.shared.geo.CityEntity;
import com.tweakdapp.backend.shared.geo.CountryEntity;
import com.tweakdapp.backend.profile.internal.entity.LanguageOptionEntity;
import com.tweakdapp.backend.profile.internal.entity.NotificationPreferencesEntity;
import com.tweakdapp.backend.profile.internal.entity.ProfileEntity;
import com.tweakdapp.backend.shared.geo.CityRepository;
import com.tweakdapp.backend.shared.geo.CountryRepository;
import com.tweakdapp.backend.profile.internal.repository.NotificationPreferencesRepository;
import com.tweakdapp.backend.profile.internal.repository.LanguageOptionRepository;
import com.tweakdapp.backend.profile.internal.repository.ProfileRepository;
import com.tweakdapp.backend.report.ReportService;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.tweakdapp.backend.shared.blocking.BlockDirectory;

/**
 * Business rules of {@link ProfileServiceImpl} with mocked repositories/collaborators: own-profile
 * read and not-found mapping, onboarding (username-taken pre-check and DB-race fallback, location),
 * public lookups and prefix search (blank short-circuit), notification-preference lazy defaulting,
 * moderation snapshot + ban/unban cache eviction, location updates, and self-report guarding.
 */
class ProfileServiceImplTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String USER_S = USER.toString();
    private static final UUID PEER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** When the account was created: a while ago, and deliberately not "now" — the point of these tests. */
    private static final Instant SIGNED_UP_AT = Instant.parse("2026-03-14T08:30:00Z");

    private ProfileRepository profileRepository;
    private CountryRepository countryRepository;
    private CityRepository cityRepository;
    private NotificationPreferencesRepository notificationPreferencesRepository;
    private LanguageOptionRepository languageOptionRepository;
    private ReportService reportService;
    private StorageService storageService;
    private BadgeService badgeService;
    private BanCache banCache;
    private BlockDirectory blockDirectory;

    private ProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        profileRepository = mock(ProfileRepository.class);
        countryRepository = mock(CountryRepository.class);
        cityRepository = mock(CityRepository.class);
        notificationPreferencesRepository = mock(NotificationPreferencesRepository.class);
        languageOptionRepository = mock(LanguageOptionRepository.class);
        reportService = mock(ReportService.class);
        storageService = mock(StorageService.class);
        banCache = mock(BanCache.class);
        blockDirectory = mock(BlockDirectory.class);
        // Every profile DTO carries its owner's badges; these fixtures have none.
        badgeService = mock(BadgeService.class);
        when(badgeService.listUserBadges(any())).thenReturn(List.of());
        // Real mapper over a mocked StorageService: avatar values in these fixtures are blank or
        // http URLs, so publicUrl() is never hit — but the mapper still exercises the real resolution.
        ProfileDtoMapper mapper = new ProfileDtoMapper(storageService);
        service = new ProfileServiceImpl(profileRepository, countryRepository, cityRepository,
                notificationPreferencesRepository, languageOptionRepository,
                reportService, storageService, mapper, badgeService, banCache, blockDirectory);
    }

    // ---- fixture helpers ----------------------------------------------------

    private ProfileEntity profileWithCity() {
        CityEntity city = new CityEntity();
        city.setId("cluj");
        ProfileEntity p = new ProfileEntity();
        p.setId(USER);
        p.setUsername("olduser");
        p.setName("Old Name");
        p.setCity(city);
        p.setDiscoveryRadiusKm(10);
        return p;
    }

    // ---- getProfile ---------------------------------------------------------

    @Test
    void getProfileReturnsOwnProfileAsDto() {
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profileWithCity()));

        ProfileDto dto = service.getProfile(USER_S);

        assertThat(dto.id()).isEqualTo(USER);
        assertThat(dto.username()).isEqualTo("olduser");
        assertThat(dto.cityId()).isEqualTo("cluj");
    }

    @Test
    void getProfileThrowsWhenProfileIsMissing() {
        when(profileRepository.findById(USER)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.getProfile(USER_S));
    }

    // ---- existsByUsername ---------------------------------------------------

    @Test
    void existsByUsernameDelegatesToRepository() {
        when(profileRepository.existsByUsername("taken")).thenReturn(true);

        assertThat(service.existsByUsername("taken")).isTrue();
    }

    // ---- completeOnboarding -------------------------------------------------

    private OnboardingRequest onboarding(String username, String cityId) {
        return new OnboardingRequest("New User", username, "my bio", cityId, 25);
    }

    @Test
    void completeOnboardingSetsUsernameBioLocationAndClearsOnboardingFlag() {
        ProfileEntity profile = profileWithCity();
        profile.setRequiresOnboarding(true);
        CityEntity city = new CityEntity();
        city.setId("cluj");
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profile));
        when(profileRepository.existsByUsername("newuser")).thenReturn(false);
        when(cityRepository.findById("cluj")).thenReturn(Optional.of(city));
        when(notificationPreferencesRepository.existsById(USER)).thenReturn(false);

        ProfileDto dto = service.completeOnboarding(USER_S,
                onboarding("newuser", "cluj"));

        assertThat(dto.username()).isEqualTo("newuser");
        assertThat(profile.getName()).isEqualTo("New User");
        assertThat(profile.getBio()).isEqualTo("my bio");
        assertThat(profile.isRequiresOnboarding()).isFalse();
        assertThat(profile.getDiscoveryRadiusKm()).isEqualTo(25);
        verify(profileRepository).saveAndFlush(profile);
        verify(notificationPreferencesRepository).save(any(NotificationPreferencesEntity.class));
    }

    /**
     * Where the backend's automatic badges are actually handed out. Signup happens inside Supabase —
     * the {@code handle_new_user} trigger inserts the profile and no Java code runs — so onboarding
     * is the first moment this can happen, and it is also the moment there is a member rather than
     * an abandoned signup.
     *
     * <p>Note what is <em>not</em> asserted: any badge id. This module reports the event and names
     * no badge, so adding or withdrawing a signup badge is a row in the catalogue and never a change
     * here.
     */
    @Test
    void completeOnboardingReportsTheNewMemberToBadges() {
        ProfileEntity profile = profileWithCity();
        profile.setCreatedAt(SIGNED_UP_AT);
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profile));
        when(profileRepository.existsByUsername("newuser")).thenReturn(false);
        when(cityRepository.findById("cluj")).thenReturn(Optional.of(cityCluj()));
        when(notificationPreferencesRepository.existsById(USER)).thenReturn(false);

        service.completeOnboarding(USER_S, onboarding("newuser", "cluj"));

        verify(badgeService).awardForTrigger(USER, BadgeTrigger.ACCOUNT_CREATED, SIGNED_UP_AT);
    }

    /**
     * The moment reported is the account's <strong>creation date</strong>, not now — which is what
     * makes a limited-time badge fair. Someone who signed up two days before a cutoff and finished
     * onboarding a week after it still earned the badge by signing up in time; judging against the
     * onboarding moment would quietly take it away from them.
     */
    @Test
    void onboardingJudgesSignupBadgesAgainstWhenTheAccountWasCreated() {
        ProfileEntity profile = profileWithCity();
        profile.setCreatedAt(SIGNED_UP_AT);
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profile));
        when(profileRepository.existsByUsername("newuser")).thenReturn(false);
        when(cityRepository.findById("cluj")).thenReturn(Optional.of(cityCluj()));
        when(notificationPreferencesRepository.existsById(USER)).thenReturn(false);

        service.completeOnboarding(USER_S, onboarding("newuser", "cluj"));

        ArgumentCaptor<Instant> at = ArgumentCaptor.forClass(Instant.class);
        verify(badgeService).awardForTrigger(eq(USER), eq(BadgeTrigger.ACCOUNT_CREATED), at.capture());
        assertThat(at.getValue()).isEqualTo(SIGNED_UP_AT);
        assertThat(at.getValue()).isBefore(Instant.now().minusSeconds(3600));
    }

    /** Onboarding that never happened cannot have produced a member, so it awards nothing. */
    @Test
    void noBadgeIsReportedWhenOnboardingIsRejected() {
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profileWithCity()));
        when(profileRepository.existsByUsername("newuser")).thenReturn(true);

        assertThatExceptionOfType(UsernameAlreadyTakenException.class)
                .isThrownBy(() -> service.completeOnboarding(USER_S, onboarding("newuser", "cluj")));

        verify(badgeService, never()).awardForTrigger(any(), any(), any());
    }

    @Test
    void completeOnboardingRejectsAlreadyTakenUsernameBeforeSaving() {
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profileWithCity()));
        when(profileRepository.existsByUsername("newuser")).thenReturn(true);

        assertThatExceptionOfType(UsernameAlreadyTakenException.class)
                .isThrownBy(() -> service.completeOnboarding(USER_S,
                        onboarding("newuser", "cluj")));

        verify(profileRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeOnboardingMapsDbUniqueViolationToUsernameTaken() {
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profileWithCity()));
        when(profileRepository.existsByUsername("newuser")).thenReturn(false);
        when(cityRepository.findById("cluj")).thenReturn(Optional.of(cityCluj()));
        when(profileRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatExceptionOfType(UsernameAlreadyTakenException.class)
                .isThrownBy(() -> service.completeOnboarding(USER_S,
                        onboarding("newuser", "cluj")));
    }

    @Test
    void completeOnboardingRejectsUnknownCity() {
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profileWithCity()));
        when(profileRepository.existsByUsername("newuser")).thenReturn(false);
        when(cityRepository.findById("nowhere")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidReferenceException.class)
                .isThrownBy(() -> service.completeOnboarding(USER_S,
                        onboarding("newuser", "nowhere")));
    }

    @Test
    void completeOnboardingThrowsWhenProfileMissing() {
        when(profileRepository.findById(USER)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.completeOnboarding(USER_S,
                        onboarding("newuser", "cluj")));
    }

    private CityEntity cityCluj() {
        CityEntity city = new CityEntity();
        city.setId("cluj");
        return city;
    }

    // ---- getPublicProfileByUsername -----------------------------------------

    @Test
    void getPublicProfileReturnsPublicViewByUsername() {
        ProfileEntity p = profileWithCity();
        p.setUsername("carsguy");
        when(profileRepository.findByUsername("carsguy")).thenReturn(Optional.of(p));

        PublicProfileDto dto = service.getPublicProfileByUsername(PEER, "carsguy");

        assertThat(dto.username()).isEqualTo("carsguy");
        assertThat(dto.id()).isEqualTo(USER);
    }

    @Test
    void getPublicProfileThrowsWhenUsernameUnknown() {
        when(profileRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.getPublicProfileByUsername(PEER, "ghost"));
    }

    // ---- searchByUsername ---------------------------------------------------

    @Test
    void searchByUsernameReturnsEmptyForBlankPrefixWithoutQuerying() {
        assertThat(service.searchByUsername(PEER, "   ")).isEmpty();
        assertThat(service.searchByUsername(PEER, null)).isEmpty();
        verify(profileRepository, never()).findTop20ByUsernameStartingWithIgnoreCaseAndIdNotInOrderByUsernameAsc(any(), any());
    }

    @Test
    void searchByUsernameMapsResultsToSearchDtos() {
        ProfileEntity p = profileWithCity();
        p.setUsername("carla");
        when(profileRepository.findTop20ByUsernameStartingWithIgnoreCaseAndIdNotInOrderByUsernameAsc(
                "car", List.of(BlockDirectory.NOBODY)))
                .thenReturn(List.of(p));

        List<ProfileSearchResultDto> results = service.searchByUsername(PEER, "car");

        assertThat(results).singleElement()
                .satisfies(r -> assertThat(r.username()).isEqualTo("carla"));
    }

    // ---- findIdByUsername / findByIds / findBusinessProfileIds --------------

    @Test
    void findIdByUsernameResolvesToTheId() {
        ProfileEntity p = profileWithCity();
        when(profileRepository.findByUsername("olduser")).thenReturn(Optional.of(p));

        assertThat(service.findIdByUsername("olduser")).contains(USER);
    }

    @Test
    void findByIdsReturnsEmptyForEmptyInputWithoutQuerying() {
        assertThat(service.findByIds(List.of())).isEmpty();
        assertThat(service.findByIds(null)).isEmpty();
        verify(profileRepository, never()).findAllByIdIn(any());
    }

    @Test
    void findBusinessProfileIdsReturnsEmptyForEmptyInputWithoutQuerying() {
        assertThat(service.findBusinessProfileIds(List.of())).isEmpty();
        verify(profileRepository, never()).findBusinessIds(any());
    }

    @Test
    void findBusinessProfileIdsDelegatesForNonEmptyInput() {
        when(profileRepository.findBusinessIds(List.of(USER, PEER))).thenReturn(List.of(PEER));

        assertThat(service.findBusinessProfileIds(List.of(USER, PEER))).containsExactly(PEER);
    }

    // ---- moderation snapshot + ban/unban ------------------------------------

    @Test
    void findModerationSnapshotMapsAllFields() {
        ProfileEntity p = profileWithCity();
        p.setName("Racer");
        p.setFollowersCount(42);
        p.setBusiness(true);
        p.setBanned(true);
        Instant until = Instant.parse("2026-08-01T00:00:00Z");
        p.setBannedUntil(until);
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));

        Optional<ProfileModerationSnapshotDto> snap = service.findModerationSnapshot(USER);

        assertThat(snap).get().satisfies(s -> {
            assertThat(s.followersCount()).isEqualTo(42);
            assertThat(s.business()).isTrue();
            assertThat(s.banned()).isTrue();
            assertThat(s.bannedUntil()).isEqualTo(until);
        });
    }

    @Test
    void findModerationSnapshotIsEmptyWhenProfileGone() {
        when(profileRepository.findById(USER)).thenReturn(Optional.empty());

        assertThat(service.findModerationSnapshot(USER)).isEmpty();
    }

    @Test
    void banUserSetsBanStateAndEvictsCache() {
        ProfileEntity p = profileWithCity();
        Instant until = Instant.parse("2026-08-01T00:00:00Z");
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));

        service.banUser(USER, until);

        assertThat(p.isBanned()).isTrue();
        assertThat(p.getBannedUntil()).isEqualTo(until);
        verify(banCache).evict(USER);
    }

    @Test
    void banUserThrowsWhenProfileMissing() {
        when(profileRepository.findById(USER)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.banUser(USER, null));
        verify(banCache, never()).evict(any());
    }

    @Test
    void unbanUserClearsBanStateAndEvictsCache() {
        ProfileEntity p = profileWithCity();
        p.setBanned(true);
        p.setBannedUntil(Instant.parse("2026-08-01T00:00:00Z"));
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));

        service.unbanUser(USER);

        assertThat(p.isBanned()).isFalse();
        assertThat(p.getBannedUntil()).isNull();
        verify(banCache).evict(USER);
    }

    // ---- reference lists ----------------------------------------------------

    @Test
    void listCountriesMapsEntitiesToDtos() {
        CountryEntity ro = new CountryEntity();
        ro.setId("RO");
        ro.setName("Romania");
        when(countryRepository.findAllByOrderByNameAsc()).thenReturn(List.of(ro));

        List<CountryDto> countries = service.listCountries();

        assertThat(countries).singleElement()
                .isEqualTo(new CountryDto("RO", "Romania"));
    }

    // ---- updateLocation -----------------------------------------------------

    @Test
    void updateLocationAppliesCityAndRadiusThenSaves() {
        ProfileEntity p = profileWithCity();
        CityEntity newCity = new CityEntity();
        newCity.setId("bucharest");
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));
        when(cityRepository.findById("bucharest")).thenReturn(Optional.of(newCity));

        ProfileDto dto = service.updateLocation(USER_S, new LocationRequest("bucharest", 50));

        assertThat(p.getCity().getId()).isEqualTo("bucharest");
        assertThat(p.getDiscoveryRadiusKm()).isEqualTo(50);
        assertThat(dto.cityId()).isEqualTo("bucharest");
        verify(profileRepository).save(p);
    }

    @Test
    void updateLocationLeavesFieldsUntouchedWhenRequestIsEmpty() {
        ProfileEntity p = profileWithCity();
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));

        service.updateLocation(USER_S, new LocationRequest(null, null));

        assertThat(p.getCity().getId()).isEqualTo("cluj");
        assertThat(p.getDiscoveryRadiusKm()).isEqualTo(10);
        verify(cityRepository, never()).findById(any());
    }

    @Test
    void updateLocationRejectsUnknownCity() {
        when(profileRepository.findById(USER)).thenReturn(Optional.of(profileWithCity()));
        when(cityRepository.findById("nowhere")).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidReferenceException.class)
                .isThrownBy(() -> service.updateLocation(USER_S, new LocationRequest("nowhere", null)));
    }

    // ---- updateRealtimeLocation ---------------------------------------------

    @Test
    void updateRealtimeLocationStoresAPointInLngLatOrder() {
        ProfileEntity p = profileWithCity();
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));

        service.updateRealtimeLocation(USER_S, new RealtimeLocationRequest(46.77, 23.59));

        assertThat(p.getRealtimeLocation()).isNotNull();
        assertThat(p.getRealtimeLocation().getX()).isEqualTo(23.59); // lng
        assertThat(p.getRealtimeLocation().getY()).isEqualTo(46.77); // lat
        verify(profileRepository).save(p);
    }

    @Test
    void updateRealtimeLocationThrowsWhenProfileMissing() {
        when(profileRepository.findById(USER)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.updateRealtimeLocation(USER_S, new RealtimeLocationRequest(1.0, 2.0)));
    }

    // ---- notification preferences -------------------------------------------

    @Test
    void getNotificationPreferencesReturnsStoredRow() {
        NotificationPreferencesEntity prefs = new NotificationPreferencesEntity();
        prefs.setProfileId(USER);
        prefs.setLikesEnabled(false);
        when(notificationPreferencesRepository.findById(USER)).thenReturn(Optional.of(prefs));

        NotificationPreferencesDto dto = service.getNotificationPreferences(USER_S);

        assertThat(dto.likesEnabled()).isFalse();
        assertThat(dto.commentsEnabled()).isTrue();
        verify(notificationPreferencesRepository, never()).save(any());
    }

    @Test
    void getNotificationPreferencesLazilyCreatesDefaultsWhenMissing() {
        when(notificationPreferencesRepository.findById(USER)).thenReturn(Optional.empty());
        when(profileRepository.existsById(USER)).thenReturn(true);
        when(notificationPreferencesRepository.save(any(NotificationPreferencesEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferencesDto dto = service.getNotificationPreferences(USER_S);

        assertThat(dto.likesEnabled()).isTrue();
        assertThat(dto.serviceRemindersEnabled()).isTrue();
        verify(notificationPreferencesRepository).save(any(NotificationPreferencesEntity.class));
    }

    @Test
    void getNotificationPreferencesThrowsWhenNoRowAndNoProfile() {
        when(notificationPreferencesRepository.findById(USER)).thenReturn(Optional.empty());
        when(profileRepository.existsById(USER)).thenReturn(false);

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.getNotificationPreferences(USER_S));
    }

    @Test
    void getNotificationPreferencesOrDefaultReturnsStoredRowWithoutWriting() {
        NotificationPreferencesEntity prefs = new NotificationPreferencesEntity();
        prefs.setProfileId(USER);
        prefs.setLikesEnabled(false);
        prefs.setSharesEnabled(false);
        when(notificationPreferencesRepository.findById(USER)).thenReturn(Optional.of(prefs));

        NotificationPreferencesDto dto = service.getNotificationPreferencesOrDefault(USER);

        assertThat(dto.likesEnabled()).isFalse();
        assertThat(dto.sharesEnabled()).isFalse();
        assertThat(dto.commentsEnabled()).isTrue();
        verify(notificationPreferencesRepository, never()).save(any());
    }

    @Test
    void getNotificationPreferencesOrDefaultReadsAllEnabledWhenMissingRowWithoutWritingOrThrowing() {
        // A missing preferences row must behave like the module default (all enabled) — and, unlike
        // getNotificationPreferences, it must neither lazily create the row nor touch the profile.
        when(notificationPreferencesRepository.findById(USER)).thenReturn(Optional.empty());

        NotificationPreferencesDto dto = service.getNotificationPreferencesOrDefault(USER);

        assertThat(dto.likesEnabled()).isTrue();
        assertThat(dto.commentsEnabled()).isTrue();
        assertThat(dto.sharesEnabled()).isTrue();
        verify(notificationPreferencesRepository, never()).save(any());
        verify(profileRepository, never()).existsById(any());
    }

    @Test
    void updateNotificationPreferencesReplacesEveryToggle() {
        NotificationPreferencesEntity prefs = new NotificationPreferencesEntity();
        prefs.setProfileId(USER);
        when(notificationPreferencesRepository.findById(USER)).thenReturn(Optional.of(prefs));
        when(notificationPreferencesRepository.save(any(NotificationPreferencesEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationPreferencesDto dto = service.updateNotificationPreferences(USER_S,
                new NotificationPreferencesRequest(false, false, true, true, false, true, false, true, true));

        assertThat(dto.likesEnabled()).isFalse();
        assertThat(dto.sharesEnabled()).isTrue();
        assertThat(dto.serviceRemindersEnabled()).isFalse();
        assertThat(prefs.getUpdatedAt()).isNotNull();
    }

    // ---- updateProfile (name/bio partial edit) ------------------------------

    @Test
    void updateProfileAppliesOnlyProvidedFieldsAndReturnsUpdatedDto() {
        ProfileEntity p = profileWithCity();
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));

        ProfileDto dto = service.updateProfile(USER_S, new ProfileEditRequest("New Name", "new bio"));

        assertThat(p.getName()).isEqualTo("New Name");
        assertThat(p.getBio()).isEqualTo("new bio");
        assertThat(dto.name()).isEqualTo("New Name");
        verify(profileRepository).save(p);
    }

    @Test
    void updateProfileLeavesNullFieldsUnchanged() {
        ProfileEntity p = profileWithCity();
        p.setName("Old Name");
        p.setBio("old bio");
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));

        service.updateProfile(USER_S, new ProfileEditRequest(null, null));

        assertThat(p.getName()).isEqualTo("Old Name");
        assertThat(p.getBio()).isEqualTo("old bio");
    }

    @Test
    void updateProfileAllowsClearingWithEmptyStrings() {
        ProfileEntity p = profileWithCity();
        p.setName("Old Name");
        p.setBio("old bio");
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));

        service.updateProfile(USER_S, new ProfileEditRequest("", ""));

        assertThat(p.getName()).isEmpty();
        assertThat(p.getBio()).isEmpty();
    }

    @Test
    void updateProfileThrowsWhenProfileMissing() {
        when(profileRepository.findById(USER)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.updateProfile(USER_S, new ProfileEditRequest("x", null)));
    }

    // ---- updateAvatar -------------------------------------------------------

    @Test
    void updateAvatarStoresKeyAndDeletesPreviousR2ObjectAfterCommit() {
        ProfileEntity p = profileWithCity();
        p.setAvatarUrl("avatars/u/old.webp");
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));
        when(storageService.publicUrl(StorageBucket.AVATARS, "avatars/" + USER_S + "/new.webp"))
                .thenReturn("http://cdn/avatars/u/new.webp");

        TransactionSynchronizationManager.initSynchronization();
        try {
            ProfileDto dto = service.updateAvatar(USER_S, "avatars/" + USER_S + "/new.webp");

            assertThat(p.getAvatarUrl()).isEqualTo("avatars/" + USER_S + "/new.webp");
            assertThat(dto.avatarUrl()).isEqualTo("http://cdn/avatars/u/new.webp");
            verify(profileRepository).save(p);
            // deletion is deferred until the surrounding transaction commits
            verify(storageService, never()).deleteByKeys(any(), anyList());
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(storageService).deleteByKeys(StorageBucket.AVATARS, List.of("avatars/u/old.webp"));
    }

    @Test
    void updateAvatarDoesNotDeleteWhenPreviousWasBlankOrLegacyHttpUrl() {
        ProfileEntity p = profileWithCity();
        p.setAvatarUrl("https://lh3.googleusercontent.com/a/photo.jpg");
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateAvatar(USER_S, "avatars/" + USER_S + "/new.webp");
            // legacy http value ⇒ no synchronization registered
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(storageService, never()).deleteByKeys(any(), anyList());
    }

    @Test
    void updateAvatarRejectsKeyOutsideTheCallersNamespace() {
        assertThatExceptionOfType(InvalidReferenceException.class)
                .isThrownBy(() -> service.updateAvatar(USER_S, "avatars/" + UUID.randomUUID() + "/new.webp"));

        verify(profileRepository, never()).save(any());
    }

    @Test
    void updateAvatarThrowsWhenProfileMissing() {
        when(profileRepository.findById(USER)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.updateAvatar(USER_S, "avatars/" + USER_S + "/new.webp"));
    }

    // ---- language -----------------------------------------------------------

    @Test
    void listLanguageOptionsMapsEntitiesToDtos() {
        LanguageOptionEntity en = new LanguageOptionEntity();
        en.setId("en");
        en.setLanguage("English");
        when(languageOptionRepository.findAllByOrderByLanguageAsc()).thenReturn(List.of(en));

        List<LanguageOptionDto> options = service.listLanguageOptions();

        assertThat(options).singleElement().isEqualTo(new LanguageOptionDto("en", "English"));
    }

    @Test
    void updateLanguageSetsCodeWhenItExistsAndReturnsUpdatedDto() {
        ProfileEntity p = profileWithCity();
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));
        when(languageOptionRepository.existsById("ro")).thenReturn(true);

        ProfileDto dto = service.updateLanguage(USER_S, "ro");

        assertThat(p.getAppLanguage()).isEqualTo("ro");
        assertThat(dto.appLanguage()).isEqualTo("ro");
        verify(profileRepository).save(p);
    }

    @Test
    void updateLanguageRejectsUnknownCode() {
        ProfileEntity p = profileWithCity();
        when(profileRepository.findById(USER)).thenReturn(Optional.of(p));
        when(languageOptionRepository.existsById("xx")).thenReturn(false);

        assertThatExceptionOfType(InvalidReferenceException.class)
                .isThrownBy(() -> service.updateLanguage(USER_S, "xx"));
        assertThat(p.getAppLanguage()).isNull();
        verify(profileRepository, never()).save(any());
    }

    @Test
    void updateLanguageThrowsWhenProfileMissing() {
        when(profileRepository.findById(USER)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.updateLanguage(USER_S, "ro"));
    }

    // ---- reportProfile ------------------------------------------------------

    @Test
    void reportProfileResolvesUsernameAndDelegatesToReportService() {
        ProfileEntity reported = profileWithCity();
        reported.setId(PEER);
        when(profileRepository.findByUsername("target")).thenReturn(Optional.of(reported));
        UUID reasonId = UUID.fromString("00000000-0000-0000-0000-0000000000f0");

        service.reportProfile(USER_S, "target", reasonId);

        verify(reportService).reportProfile(USER, PEER, reasonId);
    }

    @Test
    void reportProfileRejectsSelfReport() {
        ProfileEntity self = profileWithCity();
        self.setId(USER);
        when(profileRepository.findByUsername("me")).thenReturn(Optional.of(self));

        assertThatExceptionOfType(CannotReportSelfException.class)
                .isThrownBy(() -> service.reportProfile(USER_S, "me", null));
        verifyNoInteractions(reportService);
    }

    @Test
    void reportProfileThrowsWhenUsernameUnknown() {
        when(profileRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatExceptionOfType(ProfileNotFoundException.class)
                .isThrownBy(() -> service.reportProfile(USER_S, "ghost", null));
        verifyNoInteractions(reportService);
    }
}
