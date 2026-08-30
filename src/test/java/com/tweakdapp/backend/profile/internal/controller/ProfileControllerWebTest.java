package com.tweakdapp.backend.profile.internal.controller;

import com.tweakdapp.backend.profile.ProfileService;
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
import com.tweakdapp.backend.profile.exception.InvalidReferenceException;
import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.profile.exception.UsernameAlreadyTakenException;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST surface of {@link ProfileController}: auth requirement, JWT-subject delegation, snake_case
 * DTO shapes, bean-validation 400s that short-circuit the service, and the exception→status mapping
 * (not-found 404, username-taken 409, invalid reference 400).
 */
@AppWebMvcTest(ProfileController.class)
class ProfileControllerWebTest {

    private static final UUID ID = TestJwts.USER_ID;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    private ProfileDto sampleProfile() {
        return new ProfileDto(ID, "user", "Racer", "racer", "http://a/x.png", "vroom",
                "http://link", 12, 7, true, false, false, "cluj", 25, "en");
    }

    // ---- auth ---------------------------------------------------------------

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/profile/me"))
                .andExpect(status().isUnauthorized());
    }

    // ---- GET /me ------------------------------------------------------------

    @Test
    void getMeReturnsOwnProfileWithSnakeCaseFieldsAndPassesTheJwtSubject() throws Exception {
        when(profileService.getProfile(ID.toString())).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/profile/me").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("racer"))
                .andExpect(jsonPath("$.avatar_url").value("http://a/x.png"))
                .andExpect(jsonPath("$.followers_count").value(12))
                .andExpect(jsonPath("$.following_count").value(7))
                .andExpect(jsonPath("$.is_verified").value(true))
                .andExpect(jsonPath("$.is_business").value(false))
                .andExpect(jsonPath("$.requires_onboarding").value(false))
                .andExpect(jsonPath("$.city_id").value("cluj"))
                .andExpect(jsonPath("$.discovery_radius_km").value(25));

        verify(profileService).getProfile(ID.toString());
    }

    @Test
    void getMeSurfacesNotFoundAs404() throws Exception {
        when(profileService.getProfile(ID.toString())).thenThrow(ProfileNotFoundException.byUserId(ID.toString()));

        mockMvc.perform(get("/api/v1/profile/me").with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }

    // ---- PATCH /me (name/bio edit) ------------------------------------------

    @Test
    void updateProfileDelegatesTrimmedNameAndBioAndReturnsProfile() throws Exception {
        when(profileService.updateProfile(eq(ID.toString()), any(ProfileEditRequest.class)))
                .thenReturn(sampleProfile());

        mockMvc.perform(patch("/api/v1/profile/me")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  New Name  \",\"bio\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("racer"));

        ArgumentCaptor<ProfileEditRequest> req = ArgumentCaptor.forClass(ProfileEditRequest.class);
        verify(profileService).updateProfile(eq(ID.toString()), req.capture());
        assertThat(req.getValue().name()).isEqualTo("New Name"); // trimmed
        assertThat(req.getValue().bio()).isEqualTo("hello");
    }

    @Test
    void updateProfileWithEmptyBodyLeavesFieldsNull() throws Exception {
        when(profileService.updateProfile(eq(ID.toString()), any(ProfileEditRequest.class)))
                .thenReturn(sampleProfile());

        mockMvc.perform(patch("/api/v1/profile/me")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        ArgumentCaptor<ProfileEditRequest> req = ArgumentCaptor.forClass(ProfileEditRequest.class);
        verify(profileService).updateProfile(eq(ID.toString()), req.capture());
        assertThat(req.getValue().name()).isNull();
        assertThat(req.getValue().bio()).isNull();
    }

    @Test
    void updateProfileWithOverLongNameIsRejectedBeforeReachingTheService() throws Exception {
        String longName = "a".repeat(81);
        mockMvc.perform(patch("/api/v1/profile/me")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + longName + "\"}"))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).updateProfile(any(), any());
    }

    @Test
    void updateProfileWithOverLongBioIsRejectedBeforeReachingTheService() throws Exception {
        String longBio = "b".repeat(501);
        mockMvc.perform(patch("/api/v1/profile/me")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"" + longBio + "\"}"))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).updateProfile(any(), any());
    }

    // ---- PATCH /me/avatar ---------------------------------------------------

    @Test
    void updateAvatarDelegatesTheKeyAndReturnsProfile() throws Exception {
        when(profileService.updateAvatar(ID.toString(), "avatars/u/pic.webp"))
                .thenReturn(sampleProfile());

        mockMvc.perform(patch("/api/v1/profile/me/avatar")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"avatars/u/pic.webp\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar_url").value("http://a/x.png"));

        verify(profileService).updateAvatar(ID.toString(), "avatars/u/pic.webp");
    }

    @Test
    void updateAvatarWithBlankKeyIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(patch("/api/v1/profile/me/avatar")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).updateAvatar(any(), any());
    }

    // ---- language -----------------------------------------------------------

    @Test
    void getLanguageOptionsReturnsList() throws Exception {
        when(profileService.listLanguageOptions())
                .thenReturn(List.of(new LanguageOptionDto("en", "English"),
                        new LanguageOptionDto("ro", "Romanian")));

        mockMvc.perform(get("/api/v1/profile/language-options").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("en"))
                .andExpect(jsonPath("$[0].language").value("English"))
                .andExpect(jsonPath("$[1].id").value("ro"));
    }

    @Test
    void updateLanguageDelegatesTheCodeAndReturnsProfile() throws Exception {
        when(profileService.updateLanguage(ID.toString(), "ro")).thenReturn(sampleProfile());

        mockMvc.perform(patch("/api/v1/profile/me/language")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language_id\":\"ro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app_language").value("en"));

        verify(profileService).updateLanguage(ID.toString(), "ro");
    }

    @Test
    void updateLanguageSurfacesUnknownCodeAs400() throws Exception {
        when(profileService.updateLanguage(ID.toString(), "xx"))
                .thenThrow(new InvalidReferenceException("Unknown language: xx"));

        mockMvc.perform(patch("/api/v1/profile/me/language")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language_id\":\"xx\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateLanguageWithBlankCodeIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(patch("/api/v1/profile/me/language")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language_id\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).updateLanguage(any(), any());
    }

    // ---- POST /onboarding ---------------------------------------------------

    private static final String VALID_ONBOARDING = """
            {"name":"New Racer","username":"newracer","bio":"hi","city_id":"cluj","discovery_radius_km":25}""";

    @Test
    void onboardingReturnsProfileAndPassesTheSubjectAndBody() throws Exception {
        when(profileService.completeOnboarding(eq(ID.toString()), any(OnboardingRequest.class)))
                .thenReturn(sampleProfile());

        mockMvc.perform(post("/api/v1/profile/onboarding")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ONBOARDING))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("racer"));

        ArgumentCaptor<OnboardingRequest> req = ArgumentCaptor.forClass(OnboardingRequest.class);
        verify(profileService).completeOnboarding(eq(ID.toString()), req.capture());
        assertThat(req.getValue().name()).isEqualTo("New Racer");
        assertThat(req.getValue().username()).isEqualTo("newracer");
        assertThat(req.getValue().cityId()).isEqualTo("cluj");
    }

    @Test
    void onboardingSurfacesUsernameTakenAs409() throws Exception {
        when(profileService.completeOnboarding(eq(ID.toString()), any(OnboardingRequest.class)))
                .thenThrow(new UsernameAlreadyTakenException("newracer"));

        mockMvc.perform(post("/api/v1/profile/onboarding")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_ONBOARDING))
                .andExpect(status().isConflict());
    }

    @Test
    void onboardingWithBlankUsernameIsRejectedBeforeReachingTheService() throws Exception {
        String body = """
                {"name":"New Racer","username":"","city_id":"cluj","discovery_radius_km":25}""";

        mockMvc.perform(post("/api/v1/profile/onboarding")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).completeOnboarding(any(), any());
    }

    @Test
    void onboardingWithBlankNameIsRejectedBeforeReachingTheService() throws Exception {
        String body = """
                {"name":"","username":"newracer","city_id":"cluj","discovery_radius_km":25}""";

        mockMvc.perform(post("/api/v1/profile/onboarding")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).completeOnboarding(any(), any());
    }

    @Test
    void onboardingWithInvalidUsernamePatternIsRejected() throws Exception {
        String body = """
                {"name":"New Racer","username":"Bad Name","city_id":"cluj","discovery_radius_km":25}""";

        mockMvc.perform(post("/api/v1/profile/onboarding")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).completeOnboarding(any(), any());
    }

    @Test
    void onboardingWithMissingCityIsRejected() throws Exception {
        String body = """
                {"name":"New Racer","username":"newracer","discovery_radius_km":25}""";

        mockMvc.perform(post("/api/v1/profile/onboarding")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onboardingWithOutOfRangeRadiusIsRejected() throws Exception {
        String body = """
                {"name":"New Racer","username":"newracer","city_id":"cluj","discovery_radius_km":200}""";

        mockMvc.perform(post("/api/v1/profile/onboarding")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /by-username/{username} ----------------------------------------

    @Test
    void getByUsernameReturnsPublicProfileWithoutOnboardingFlag() throws Exception {
        when(profileService.getPublicProfileByUsername("racer"))
                .thenReturn(new PublicProfileDto(ID, "Racer", "racer", "http://a/x.png", "vroom",
                        "http://link", 12, 7, true, false));

        mockMvc.perform(get("/api/v1/profile/by-username/{u}", "racer").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("racer"))
                .andExpect(jsonPath("$.followers_count").value(12))
                .andExpect(jsonPath("$.requires_onboarding").doesNotExist());
    }

    @Test
    void getByUsernameSurfacesNotFoundAs404() throws Exception {
        when(profileService.getPublicProfileByUsername("ghost"))
                .thenThrow(ProfileNotFoundException.byUsername("ghost"));

        mockMvc.perform(get("/api/v1/profile/by-username/{u}", "ghost").with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }

    // ---- GET /exists/{username} ---------------------------------------------

    @Test
    void existsByUsernameReturnsBoolean() throws Exception {
        when(profileService.existsByUsername("racer")).thenReturn(true);

        mockMvc.perform(get("/api/v1/profile/exists/{u}", "racer").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    // ---- GET /search --------------------------------------------------------

    @Test
    void searchDelegatesTheQueryAndReturnsSnakeCaseResults() throws Exception {
        when(profileService.searchByUsername("rac"))
                .thenReturn(List.of(new ProfileSearchResultDto(ID, "Race Car Rick", "racer", "http://a/x.png")));

        mockMvc.perform(get("/api/v1/profile/search").param("q", "rac").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Race Car Rick"))
                .andExpect(jsonPath("$[0].username").value("racer"))
                .andExpect(jsonPath("$[0].avatar_url").value("http://a/x.png"));

        verify(profileService).searchByUsername("rac");
    }

    @Test
    void searchWithoutQueryParamIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/profile/search").with(TestJwts.user()))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).searchByUsername(any());
    }

    // ---- PATCH /me/location -------------------------------------------------

    @Test
    void updateLocationDelegatesAndReturnsProfile() throws Exception {
        when(profileService.updateLocation(eq(ID.toString()), any(LocationRequest.class)))
                .thenReturn(sampleProfile());

        mockMvc.perform(patch("/api/v1/profile/me/location")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city_id\":\"cluj\",\"discovery_radius_km\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city_id").value("cluj"));
    }

    @Test
    void updateLocationWithOutOfRangeRadiusIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/profile/me/location")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"discovery_radius_km\":0}"))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).updateLocation(any(), any());
    }

    @Test
    void updateLocationSurfacesInvalidCityAs400() throws Exception {
        when(profileService.updateLocation(eq(ID.toString()), any(LocationRequest.class)))
                .thenThrow(new InvalidReferenceException("Unknown city: nowhere"));

        mockMvc.perform(patch("/api/v1/profile/me/location")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"city_id\":\"nowhere\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---- PATCH /me/realtime-location ----------------------------------------

    @Test
    void updateRealtimeLocationDelegatesAndReturns200() throws Exception {
        mockMvc.perform(patch("/api/v1/profile/me/realtime-location")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":46.77,\"lng\":23.59}"))
                .andExpect(status().isOk());

        ArgumentCaptor<RealtimeLocationRequest> req = ArgumentCaptor.forClass(RealtimeLocationRequest.class);
        verify(profileService).updateRealtimeLocation(eq(ID.toString()), req.capture());
        assertThat(req.getValue().lat()).isEqualTo(46.77);
        assertThat(req.getValue().lng()).isEqualTo(23.59);
    }

    @Test
    void updateRealtimeLocationWithMissingLatIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/profile/me/realtime-location")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lng\":23.59}"))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).updateRealtimeLocation(any(), any());
    }

    @Test
    void updateRealtimeLocationWithOutOfRangeLatIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/profile/me/realtime-location")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":120,\"lng\":23.59}"))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).updateRealtimeLocation(any(), any());
    }

    // ---- notification preferences -------------------------------------------

    @Test
    void getNotificationsReturnsSnakeCaseToggles() throws Exception {
        when(profileService.getNotificationPreferences(ID.toString()))
                .thenReturn(new NotificationPreferencesDto(true, false, true, true, false, true, false, true, true));

        mockMvc.perform(get("/api/v1/profile/me/notifications").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes_enabled").value(true))
                .andExpect(jsonPath("$.comments_enabled").value(false))
                .andExpect(jsonPath("$.flash_meets_enabled").value(false))
                .andExpect(jsonPath("$.service_reminders_enabled").value(false));
    }

    @Test
    void updateNotificationsDelegatesTheFullPayload() throws Exception {
        when(profileService.updateNotificationPreferences(eq(ID.toString()), any(NotificationPreferencesRequest.class)))
                .thenReturn(new NotificationPreferencesDto(true, true, true, true, true, true, true, true, true));

        String body = """
                {"likes_enabled":true,"comments_enabled":true,"shares_enabled":true,"dms_enabled":true,
                 "flash_meets_enabled":true,"organized_events_enabled":true,"service_reminders_enabled":true,
                 "tags_enabled":true,"event_organizer_enabled":true}""";

        mockMvc.perform(put("/api/v1/profile/me/notifications")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likes_enabled").value(true));
    }

    @Test
    void updateNotificationsWithMissingToggleIsRejected() throws Exception {
        String body = """
                {"likes_enabled":true,"comments_enabled":true,"shares_enabled":true,"dms_enabled":true,
                 "flash_meets_enabled":true,"organized_events_enabled":true}""";

        mockMvc.perform(put("/api/v1/profile/me/notifications")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(profileService, never()).updateNotificationPreferences(any(), any());
    }
}
