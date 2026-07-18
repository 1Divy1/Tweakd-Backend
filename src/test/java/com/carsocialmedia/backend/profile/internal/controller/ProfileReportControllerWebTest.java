package com.carsocialmedia.backend.profile.internal.controller;

import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.exception.CannotReportSelfException;
import com.carsocialmedia.backend.profile.exception.ProfileNotFoundException;
import com.carsocialmedia.backend.report.ReportService;
import com.carsocialmedia.backend.report.dto.ReportReasonDto;
import com.carsocialmedia.backend.testsupport.AppWebMvcTest;
import com.carsocialmedia.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The profile-reporting surface of {@link ProfileReportController}: preset-reason passthrough, the
 * 204 file-report flow with the JWT subject as reporter and an optional body, and the
 * exception→status mapping (self-report 400, unknown username 404).
 */
@AppWebMvcTest(ProfileReportController.class)
class ProfileReportControllerWebTest {

    private static final UUID REASON = UUID.fromString("00000000-0000-0000-0000-0000000000f0");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private ReportService reportService;

    @Test
    void unauthenticatedReportIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/profile/{u}/report", "racer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getReportReasonsPassesThroughTheReportService() throws Exception {
        when(reportService.listProfileReportReasons())
                .thenReturn(List.of(new ReportReasonDto(REASON, "Spam")));

        mockMvc.perform(get("/api/v1/profile/report-reasons").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(REASON.toString()))
                .andExpect(jsonPath("$[0].reason").value("Spam"));
    }

    @Test
    void reportWithReasonReturns204AndDelegatesWithTheJwtSubject() throws Exception {
        mockMvc.perform(post("/api/v1/profile/{u}/report", "racer")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason_id\":\"" + REASON + "\"}"))
                .andExpect(status().isNoContent());

        verify(profileService).reportProfile(TestJwts.USER_ID.toString(), "racer", REASON);
    }

    @Test
    void reportWithoutBodyReturns204AndPassesNullReason() throws Exception {
        mockMvc.perform(post("/api/v1/profile/{u}/report", "racer").with(TestJwts.user()))
                .andExpect(status().isNoContent());

        verify(profileService).reportProfile(TestJwts.USER_ID.toString(), "racer", null);
    }

    @Test
    void reportOfOwnProfileSurfacesAs400() throws Exception {
        doThrow(new CannotReportSelfException())
                .when(profileService).reportProfile(TestJwts.USER_ID.toString(), "racer", null);

        mockMvc.perform(post("/api/v1/profile/{u}/report", "racer").with(TestJwts.user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportOfUnknownUsernameSurfacesAs404() throws Exception {
        doThrow(ProfileNotFoundException.byUsername("ghost"))
                .when(profileService).reportProfile(TestJwts.USER_ID.toString(), "ghost", null);

        mockMvc.perform(post("/api/v1/profile/{u}/report", "ghost").with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }
}
