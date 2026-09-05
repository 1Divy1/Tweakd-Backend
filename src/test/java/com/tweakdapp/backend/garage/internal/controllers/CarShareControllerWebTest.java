package com.tweakdapp.backend.garage.internal.controllers;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarShareDto;
import com.tweakdapp.backend.garage.dto.CarShareQrDto;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The owner's share surface over HTTP: that none of it is reachable without a JWT, the exact
 * snake_case JSON the mobile app parses, and that {@code qr.svg} comes back as a downloadable SVG
 * rather than something a browser renders inline.
 */
@AppWebMvcTest(CarShareController.class)
class CarShareControllerWebTest {

    private static final UUID CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final String CODE = "7KQ3M9XA2F";
    private static final String BASE = "/api/v1/garage/cars/" + CAR_ID + "/share";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarageService garageService;

    private static CarShareDto share() {
        return new CarShareDto(
                CODE,
                "https://web.tweakdapp.com/c/" + CODE,
                "https://web.tweakdapp.com/c/" + CODE + "?s=qr",
                true,
                Instant.parse("2026-09-04T12:00:00Z"),
                42L,
                7L,
                Instant.parse("2026-09-04T18:30:00Z"));
    }

    // ---- auth ---------------------------------------------------------------

    /** A share code is owner-only data; an anonymous caller must not be able to probe for one. */
    @Test
    void everyRouteRequiresAuthentication() throws Exception {
        mockMvc.perform(post(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(patch(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/qr.svg")).andExpect(status().isUnauthorized());
    }

    // ---- the wire shape -----------------------------------------------------

    /** A rename in any of these is a silent client break, so they are pinned by name. */
    @Test
    void theShareLinkIsReturnedInSnakeCase() throws Exception {
        when(garageService.ensureShareLink(TestJwts.USER_ID.toString(), CAR_ID)).thenReturn(share());

        mockMvc.perform(post(BASE).with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CODE))
                .andExpect(jsonPath("$.url").value("https://web.tweakdapp.com/c/" + CODE))
                .andExpect(jsonPath("$.qr_url").value("https://web.tweakdapp.com/c/" + CODE + "?s=qr"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.view_count").value(42))
                .andExpect(jsonPath("$.qr_scan_count").value(7))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.last_viewed_at").exists());
    }

    /** POST is idempotent from the client's point of view, so it answers 200, never 201. */
    @Test
    void creatingTheLinkIsAPlainOk() throws Exception {
        when(garageService.ensureShareLink(TestJwts.USER_ID.toString(), CAR_ID)).thenReturn(share());

        mockMvc.perform(post(BASE).with(TestJwts.user())).andExpect(status().isOk());
    }

    @Test
    void getReadsWithoutCreating() throws Exception {
        when(garageService.getShareLink(TestJwts.USER_ID.toString(), CAR_ID)).thenReturn(share());

        mockMvc.perform(get(BASE).with(TestJwts.user())).andExpect(status().isOk());

        verify(garageService).getShareLink(TestJwts.USER_ID.toString(), CAR_ID);
    }

    // ---- pause / resume -----------------------------------------------------

    @Test
    void patchPassesTheEnabledFlagThrough() throws Exception {
        when(garageService.setShareLinkEnabled(TestJwts.USER_ID.toString(), CAR_ID, false))
                .thenReturn(share());

        mockMvc.perform(patch(BASE).with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        verify(garageService).setShareLinkEnabled(TestJwts.USER_ID.toString(), CAR_ID, false);
    }

    /** An omitted flag is a client bug, not "pause it": rejected rather than guessed at. */
    @Test
    void patchWithoutTheFlagIsRejected() throws Exception {
        mockMvc.perform(patch(BASE).with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---- the QR -------------------------------------------------------------

    /**
     * Content type and disposition are the whole contract here: the app hands these bytes straight
     * to the system share sheet, and the filename is what the owner ends up with in Files.
     */
    @Test
    void theQrComesBackAsADownloadableSvg() throws Exception {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>";
        when(garageService.renderShareQrSvg(TestJwts.USER_ID.toString(), CAR_ID))
                .thenReturn(new CarShareQrDto(CODE, svg.getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get(BASE + "/qr.svg").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"tweakd-" + CODE + ".svg\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(content().bytes(svg.getBytes(StandardCharsets.UTF_8)));
    }

    /** The caller's own JWT subject decides ownership — never a path or body parameter. */
    @Test
    void ownershipIsTakenFromTheJwtSubject() throws Exception {
        UUID other = UUID.fromString("00000000-0000-0000-0000-00000000dead");
        when(garageService.ensureShareLink(other.toString(), CAR_ID)).thenReturn(share());

        mockMvc.perform(post(BASE).with(TestJwts.user(other))).andExpect(status().isOk());

        verify(garageService).ensureShareLink(eq(other.toString()), eq(CAR_ID));
    }
}
