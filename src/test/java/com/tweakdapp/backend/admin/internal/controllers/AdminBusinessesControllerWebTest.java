package com.tweakdapp.backend.admin.internal.controllers;

import com.tweakdapp.backend.admin.exception.MissingCapabilityException;
import com.tweakdapp.backend.admin.exception.NotTeamMemberException;
import com.tweakdapp.backend.admin.internal.AdminAccessService;
import com.tweakdapp.backend.admin.internal.Capability;
import com.tweakdapp.backend.business.BusinessService;
import com.tweakdapp.backend.business.dto.AdminBusinessDto;
import com.tweakdapp.backend.business.dto.AdminBusinessPageDto;
import com.tweakdapp.backend.business.dto.AdminBusinessSummaryDto;
import com.tweakdapp.backend.business.exception.InvalidBusinessStatusException;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The business review REST surface: who may call it, what the JSON looks like, and that the staff
 * id reaching the service is the JWT subject rather than anything a caller can supply.
 *
 * <p>The authorization assertions matter more here than the shapes: these endpoints are the only
 * way to make a business visible in the app, so a capability check silently dropped from one of
 * them would put an unverified company on the map.
 */
@AppWebMvcTest(AdminBusinessesController.class)
class AdminBusinessesControllerWebTest {

    private static final UUID BUSINESS_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccessService access;

    @MockitoBean
    private BusinessService businessService;

    private static AdminBusinessSummaryDto summary() {
        return new AdminBusinessSummaryDto(
                BUSINESS_ID, "Willy Wash", "car_wash", "Car wash",
                "https://cdn.example/logo.png", "Str. Aviator 12", "cluj-napoca",
                "pending", "active", null, null, Instant.parse("2026-09-01T10:00:00Z"));
    }

    private static AdminBusinessDto detail(String verificationStatus, String activeStatus) {
        return new AdminBusinessDto(
                BUSINESS_ID, "Willy Wash", "car_wash", "Car wash", "Hand wash",
                "https://cdn.example/logo.png", "Str. Aviator 12", "cluj-napoca", "Cluj-Napoca",
                46.77, 23.62, "+40700000000", "hi@willy.example", "https://willy.example",
                4.5, 12, 3, "Europe/Bucharest", true, List.of(),
                verificationStatus, activeStatus, null,
                Instant.parse("2026-09-02T10:00:00Z"), TestJwts.ADMIN_ID,
                Instant.parse("2026-09-02T10:00:00Z"), Instant.parse("2026-09-01T10:00:00Z"));
    }

    // ---- auth ---------------------------------------------------------------

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/businesses"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(businessService);
    }

    @Test
    void appUsersCannotReachTheAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/admin/businesses").with(TestJwts.user()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(businessService);
    }

    @Test
    void staffWithoutTheCapabilityAreRefused() throws Exception {
        when(access.require(any(), eq(Capability.VERIFY_BUSINESSES)))
                .thenThrow(new MissingCapabilityException(Capability.VERIFY_BUSINESSES.name()));

        mockMvc.perform(post("/api/v1/admin/businesses/" + BUSINESS_ID + "/verify").with(TestJwts.admin()))
                .andExpect(status().isForbidden());

        verify(businessService, never()).verify(any(), any());
    }

    @Test
    void staffWithNoTeamRowAreRefused() throws Exception {
        when(access.require(any(), eq(Capability.VERIFY_BUSINESSES))).thenThrow(new NotTeamMemberException());

        mockMvc.perform(get("/api/v1/admin/businesses").with(TestJwts.admin()))
                .andExpect(status().isForbidden());
    }

    // ---- listing ------------------------------------------------------------

    @Test
    void listReturnsTheQueueShapeAndDefaultsToNoFilter() throws Exception {
        when(businessService.listForReview(isNull(), isNull(), isNull(), eq(20)))
                .thenReturn(new AdminBusinessPageDto(List.of(summary()), "next-token"));

        mockMvc.perform(get("/api/v1/admin/businesses").with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(BUSINESS_ID.toString()))
                .andExpect(jsonPath("$.items[0].verification_status").value("pending"))
                .andExpect(jsonPath("$.items[0].active_status").value("active"))
                .andExpect(jsonPath("$.items[0].type_label").value("Car wash"))
                .andExpect(jsonPath("$.next_cursor").value("next-token"));
    }

    @Test
    void listPassesBothFiltersThrough() throws Exception {
        when(businessService.listForReview(eq("pending"), eq("active"), eq("cur"), eq(5)))
                .thenReturn(new AdminBusinessPageDto(List.of(), null));

        mockMvc.perform(get("/api/v1/admin/businesses")
                        .param("verification_status", "pending")
                        .param("active_status", "active")
                        .param("cursor", "cur")
                        .param("size", "5")
                        .with(TestJwts.admin()))
                .andExpect(status().isOk());

        verify(businessService).listForReview("pending", "active", "cur", 5);
    }

    @Test
    void anUnknownStatusFilterIsABadRequest() throws Exception {
        when(businessService.listForReview(any(), any(), any(), anyInt()))
                .thenThrow(new InvalidBusinessStatusException("verification_status must be one of ..."));

        mockMvc.perform(get("/api/v1/admin/businesses")
                        .param("verification_status", "pendign")
                        .with(TestJwts.admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void countsExposeThePendingBadge() throws Exception {
        when(businessService.countPendingVerification()).thenReturn(7L);

        mockMvc.perform(get("/api/v1/admin/businesses/counts").with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(7));
    }

    // ---- decisions ----------------------------------------------------------

    @Test
    void verifyRecordsTheCallerFromTheJwtSubject() throws Exception {
        when(businessService.verify(eq(BUSINESS_ID), eq(TestJwts.ADMIN_ID)))
                .thenReturn(detail("verified", "active"));

        mockMvc.perform(post("/api/v1/admin/businesses/" + BUSINESS_ID + "/verify").with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification_status").value("verified"))
                .andExpect(jsonPath("$.reviewed_by").value(TestJwts.ADMIN_ID.toString()));

        verify(businessService).verify(BUSINESS_ID, TestJwts.ADMIN_ID);
    }

    @Test
    void rejectRequiresANonBlankReason() throws Exception {
        mockMvc.perform(post("/api/v1/admin/businesses/" + BUSINESS_ID + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}")
                        .with(TestJwts.admin()))
                .andExpect(status().isBadRequest());

        verify(businessService, never()).reject(any(), any(), any());
    }

    @Test
    void rejectPassesTheReasonAndTheCaller() throws Exception {
        when(businessService.reject(eq(BUSINESS_ID), eq("Address does not exist"), eq(TestJwts.ADMIN_ID)))
                .thenReturn(detail("rejected", "active"));

        mockMvc.perform(post("/api/v1/admin/businesses/" + BUSINESS_ID + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Address does not exist\"}")
                        .with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification_status").value("rejected"));

        verify(businessService).reject(BUSINESS_ID, "Address does not exist", TestJwts.ADMIN_ID);
    }

    @Test
    void activeStatusIsPatchedThroughToTheService() throws Exception {
        when(businessService.setActiveStatus(eq(BUSINESS_ID), eq("suspended"), eq(TestJwts.ADMIN_ID)))
                .thenReturn(detail("verified", "suspended"));

        mockMvc.perform(patch("/api/v1/admin/businesses/" + BUSINESS_ID + "/active-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active_status\":\"suspended\"}")
                        .with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active_status").value("suspended"));

        verify(businessService).setActiveStatus(BUSINESS_ID, "suspended", TestJwts.ADMIN_ID);
    }
}
