package com.tweakdapp.backend.admin.internal.controllers;

import com.tweakdapp.backend.admin.exception.MissingCapabilityException;
import com.tweakdapp.backend.admin.internal.AdminAccessService;
import com.tweakdapp.backend.admin.internal.Capability;
import com.tweakdapp.backend.mapevents.MapEventContestsService;
import com.tweakdapp.backend.mapevents.dto.AdminContestDto;
import com.tweakdapp.backend.mapevents.exception.ContestNotOpenException;
import com.tweakdapp.backend.mapevents.exception.InvalidContestException;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contest oversight over REST. Force-finishing pays out reputation and badges, so the capability
 * check on every route — and the fact that the staff id comes from the JWT subject, not the
 * request — are what this test exists to pin.
 */
@AppWebMvcTest(AdminContestsController.class)
class AdminContestsControllerWebTest {

    private static final UUID CONTEST_ID = UUID.fromString("11111111-2222-3333-4444-666666666666");
    private static final UUID EVENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccessService access;

    @MockitoBean
    private MapEventContestsService contestsService;

    private static AdminContestDto contest(String status) {
        return new AdminContestDto(
                CONTEST_ID, EVENT_ID, "Cluj Cars & Coffee", "finished",
                Instant.parse("2026-09-01T18:00:00Z"),
                "Best exhaust", "exhaust", "Best exhaust system", status,
                Instant.parse("2026-09-01T15:00:00Z"),
                Instant.parse("2026-09-01T17:00:00Z"),
                true, 8, 34,
                new ProfileSearchResultDto(UUID.randomUUID(), "Andrei", "andrei", null),
                Instant.parse("2026-08-30T09:00:00Z"));
    }

    // ---- auth ---------------------------------------------------------------

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/contests"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(contestsService);
    }

    @Test
    void appUsersCannotReachIt() throws Exception {
        mockMvc.perform(get("/api/v1/admin/contests").with(TestJwts.user()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(contestsService);
    }

    @Test
    void staffWithoutManageContestsCannotForceFinish() throws Exception {
        when(access.require(any(), eq(Capability.MANAGE_CONTESTS)))
                .thenThrow(new MissingCapabilityException(Capability.MANAGE_CONTESTS.name()));

        mockMvc.perform(post("/api/v1/admin/contests/" + CONTEST_ID + "/finish").with(TestJwts.admin()))
                .andExpect(status().isForbidden());

        verify(contestsService, never()).finishContestAsAdmin(any(), any());
    }

    // ---- listing ------------------------------------------------------------

    @Test
    void listDefaultsToTheOpenWorklist() throws Exception {
        when(contestsService.listContestsForReview(isNull(), eq(50))).thenReturn(List.of(contest("open")));

        mockMvc.perform(get("/api/v1/admin/contests").with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(CONTEST_ID.toString()))
                .andExpect(jsonPath("$[0].event_title").value("Cluj Cars & Coffee"))
                .andExpect(jsonPath("$[0].planned_end_passed").value(true))
                .andExpect(jsonPath("$[0].entries_count").value(8))
                .andExpect(jsonPath("$[0].created_by.username").value("andrei"));

        verify(contestsService).listContestsForReview(null, 50);
    }

    @Test
    void anUnknownStatusIsABadRequest() throws Exception {
        when(contestsService.listContestsForReview(any(), anyInt()))
                .thenThrow(new InvalidContestException("status must be one of ..."));

        mockMvc.perform(get("/api/v1/admin/contests").param("status", "nope").with(TestJwts.admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void countsExposeTheOpenBadge() throws Exception {
        when(contestsService.countOpenContests()).thenReturn(3L);

        mockMvc.perform(get("/api/v1/admin/contests/counts").with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(3));
    }

    // ---- force finish -------------------------------------------------------

    @Test
    void finishPassesTheJwtSubjectAsTheActor() throws Exception {
        when(contestsService.finishContestAsAdmin(eq(CONTEST_ID), eq(TestJwts.ADMIN_ID)))
                .thenReturn(contest("finished"));

        mockMvc.perform(post("/api/v1/admin/contests/" + CONTEST_ID + "/finish").with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("finished"));

        verify(contestsService).finishContestAsAdmin(CONTEST_ID, TestJwts.ADMIN_ID);
    }

    @Test
    void finishingAContestThatNeverOpenedIsAConflict() throws Exception {
        when(contestsService.finishContestAsAdmin(any(), any()))
                .thenThrow(new ContestNotOpenException("Voting never opened"));

        mockMvc.perform(post("/api/v1/admin/contests/" + CONTEST_ID + "/finish").with(TestJwts.admin()))
                .andExpect(status().isConflict());
    }
}
