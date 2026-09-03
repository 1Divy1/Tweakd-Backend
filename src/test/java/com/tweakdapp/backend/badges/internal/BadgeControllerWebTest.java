package com.tweakdapp.backend.badges.internal;

import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.dto.BadgeDto;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST surface of {@link BadgeController}: the auth requirement, the exact snake_case JSON the
 * mobile app parses (a rename here is a silent client break), that {@code /me} is scoped to the
 * caller's own JWT subject, and that the module exposes no write path.
 */
@AppWebMvcTest(BadgeController.class)
class BadgeControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BadgeService badgeService;

    private static BadgeDto pioneer() {
        return new BadgeDto(
                "pioneer",
                "Pioneer",
                "One of the first members of the community.",
                "https://assets.tweakd.app/badges/pioneer/badge-unlocked.svg",
                "https://assets.tweakd.app/badges/pioneer/badge-locked.svg",
                true,
                Instant.parse("2026-09-03T16:22:34Z"));
    }

    // ---- auth ---------------------------------------------------------------

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/badges/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/badges/me/locked")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/badges/catalogue")).andExpect(status().isUnauthorized());
    }

    /**
     * The module is read-only: a client must not be able to award itself a badge. The mapped paths
     * answer GET and nothing else, and there is no path at all that would take a write.
     */
    @Test
    void thereIsNoWritePath() throws Exception {
        mockMvc.perform(post("/api/v1/badges/catalogue").with(TestJwts.user()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/badges/me").with(TestJwts.user()))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(post("/api/v1/badges/pioneer").with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }

    // ---- the wire format ----------------------------------------------------

    @Test
    void aHeldBadgeSerializesEveryFieldInSnakeCase() throws Exception {
        when(badgeService.listUserBadges(eq(TestJwts.USER_ID)))
                .thenReturn(List.of(new UserBadgeDto(pioneer(), Instant.parse("2026-09-03T17:00:00Z"))));

        mockMvc.perform(get("/api/v1/badges/me").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].badge.id").value("pioneer"))
                .andExpect(jsonPath("$[0].badge.title").value("Pioneer"))
                .andExpect(jsonPath("$[0].badge.description").value("One of the first members of the community."))
                .andExpect(jsonPath("$[0].badge.unlocked_url")
                        .value("https://assets.tweakd.app/badges/pioneer/badge-unlocked.svg"))
                .andExpect(jsonPath("$[0].badge.locked_url")
                        .value("https://assets.tweakd.app/badges/pioneer/badge-locked.svg"))
                .andExpect(jsonPath("$[0].badge.available").value(true))
                .andExpect(jsonPath("$[0].earned_at").exists());
    }

    /** A badge with no locked artwork sends an explicit null, so the client can branch on it. */
    @Test
    void aMissingLockedVariantSerializesAsNull() throws Exception {
        BadgeDto noLocked = new BadgeDto("pioneer", "Pioneer", null,
                "https://assets.tweakd.app/badges/pioneer/badge-unlocked.svg", null, true, Instant.now());
        when(badgeService.listCatalogue()).thenReturn(List.of(noLocked));

        mockMvc.perform(get("/api/v1/badges/catalogue").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].locked_url").value(nullValue()))
                .andExpect(jsonPath("$[0].description").value(nullValue()));
    }

    // ---- routing ------------------------------------------------------------

    /** {@code /me} reads the caller's own subject — never a client-supplied id. */
    @Test
    void meIsScopedToTheCallersOwnJwtSubject() throws Exception {
        when(badgeService.listUserBadges(eq(TestJwts.USER_ID))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/badges/me").with(TestJwts.user()))
                .andExpect(status().isOk());

        verify(badgeService).listUserBadges(TestJwts.USER_ID);
    }

    /**
     * There is no username-keyed read here at all — another user's badges ride in that user's
     * profile response. That absence is what keeps this module free of a dependency on
     * {@code profile}, and therefore usable by it.
     */
    @Test
    void thereIsNoUsernameKeyedRead() throws Exception {
        mockMvc.perform(get("/api/v1/badges/users/{username}", "racer").with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }

    // ---- the locked section -------------------------------------------------

    /** Locked badges carry the artwork and the description that says how to earn each one. */
    @Test
    void lockedBadgesAreTheCallersOwnAndCarryTheLockedArtwork() throws Exception {
        when(badgeService.listLockedBadges(eq(TestJwts.USER_ID))).thenReturn(List.of(pioneer()));

        mockMvc.perform(get("/api/v1/badges/me/locked").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("pioneer"))
                .andExpect(jsonPath("$[0].description").value("One of the first members of the community."))
                .andExpect(jsonPath("$[0].locked_url")
                        .value("https://assets.tweakd.app/badges/pioneer/badge-locked.svg"));

        verify(badgeService).listLockedBadges(TestJwts.USER_ID);
    }

    /** No username-keyed equivalent: what someone has left to unlock is not a stranger's business. */
    @Test
    void thereIsNoWayToAskWhatSomebodyElseHasNotUnlocked() throws Exception {
        mockMvc.perform(get("/api/v1/badges/users/{username}/locked", "racer").with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }
}
