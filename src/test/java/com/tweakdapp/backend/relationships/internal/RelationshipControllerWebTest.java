package com.tweakdapp.backend.relationships.internal;

import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.relationships.RelationshipService;
import com.tweakdapp.backend.relationships.dto.FollowProfileSearchResult;
import com.tweakdapp.backend.relationships.dto.FollowStatus;
import com.tweakdapp.backend.relationships.dto.FollowStatusDto;
import com.tweakdapp.backend.relationships.exception.CannotFollowSelfException;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST surface of {@link RelationshipController}: the auth requirement, status codes
 * (200/204), JWT-subject delegation, the {@link FollowStatusDto} enum shape, the snake_case
 * {@link FollowProfileSearchResult} list shape ({@code avatar_url}, {@code is_following}), and the
 * exception→status mapping (self-follow 400, unknown username 404).
 */
@AppWebMvcTest(RelationshipController.class)
class RelationshipControllerWebTest {

    private static final String SUBJECT = TestJwts.USER_ID.toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RelationshipService relationshipService;

    // ---- auth ---------------------------------------------------------------

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/follow/{username}/status", "target"))
                .andExpect(status().isUnauthorized());
    }

    // ---- POST /{username} ---------------------------------------------------

    @Test
    void followReturnsTheStatusAndDelegatesTheJwtSubject() throws Exception {
        when(relationshipService.follow(SUBJECT, "target"))
                .thenReturn(new FollowStatusDto(FollowStatus.ACCEPTED));

        mockMvc.perform(post("/api/v1/follow/{username}", "target").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(relationshipService).follow(SUBJECT, "target");
    }

    @Test
    void followingYourselfSurfacesAs400() throws Exception {
        when(relationshipService.follow(SUBJECT, "me")).thenThrow(new CannotFollowSelfException());

        mockMvc.perform(post("/api/v1/follow/{username}", "me").with(TestJwts.user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void followingAnUnknownUserSurfacesAs404() throws Exception {
        when(relationshipService.follow(SUBJECT, "ghost"))
                .thenThrow(ProfileNotFoundException.byUsername("ghost"));

        mockMvc.perform(post("/api/v1/follow/{username}", "ghost").with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }

    // ---- DELETE /{username} -------------------------------------------------

    @Test
    void unfollowReturns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/follow/{username}", "target").with(TestJwts.user()))
                .andExpect(status().isNoContent());

        verify(relationshipService).unfollow(SUBJECT, "target");
    }

    // ---- GET /{username}/status ---------------------------------------------

    @Test
    void getStatusReturnsNotFollowingShape() throws Exception {
        when(relationshipService.getFollowStatus(SUBJECT, "target"))
                .thenReturn(new FollowStatusDto(FollowStatus.NOT_FOLLOWING));

        mockMvc.perform(get("/api/v1/follow/{username}/status", "target").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_FOLLOWING"));
    }

    // ---- DELETE /followers/{username} ---------------------------------------

    @Test
    void removeFollowerReturns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/follow/followers/{username}", "alice").with(TestJwts.user()))
                .andExpect(status().isNoContent());

        verify(relationshipService).removeFollower(SUBJECT, "alice");
    }

    @Test
    void removeFollowerSurfacesUnknownUsernameAs404() throws Exception {
        doThrow(ProfileNotFoundException.byUsername("ghost"))
                .when(relationshipService).removeFollower(SUBJECT, "ghost");

        mockMvc.perform(delete("/api/v1/follow/followers/{username}", "ghost").with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }

    // ---- GET /{username}/followers + /following -----------------------------

    @Test
    void getFollowersReturnsSnakeCaseList() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
        when(relationshipService.getFollowers(SUBJECT, "target")).thenReturn(List.of(
                new FollowProfileSearchResult(id, "alice", "alice.png", true)));

        mockMvc.perform(get("/api/v1/follow/{username}/followers", "target").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].avatar_url").value("alice.png"))
                .andExpect(jsonPath("$[0].is_following").value(true));

        verify(relationshipService).getFollowers(SUBJECT, "target");
    }

    @Test
    void getFollowingDelegatesAndReturnsList() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
        when(relationshipService.getFollowing(SUBJECT, "target")).thenReturn(List.of(
                new FollowProfileSearchResult(id, "bob", null, false)));

        mockMvc.perform(get("/api/v1/follow/{username}/following", "target").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].is_following").value(false));

        verify(relationshipService).getFollowing(SUBJECT, "target");
    }
}
