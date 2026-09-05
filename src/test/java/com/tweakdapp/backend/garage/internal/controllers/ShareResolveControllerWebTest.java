package com.tweakdapp.backend.garage.internal.controllers;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarShareResolutionDto;
import com.tweakdapp.backend.garage.dto.ShareSource;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The route the installed app hits after the OS hands it a Universal Link or App Link. Two things
 * matter: it is authenticated (an app that is signed in should land on the native car screen, not
 * on the public page), and the {@code ?s=} tag survives the hop so an in-app open of a scanned
 * sticker still counts as a scan.
 */
@AppWebMvcTest(ShareResolveController.class)
class ShareResolveControllerWebTest {

    private static final UUID CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final String CODE = "7KQ3M9XA2F";
    private static final String BASE = "/api/v1/garage/share/resolve/";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarageService garageService;

    @Test
    void resolvingRequiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE + CODE)).andExpect(status().isUnauthorized());
    }

    @Test
    void resolvingReturnsTheCarAndOwnerInSnakeCase() throws Exception {
        when(garageService.resolveShareCode(any(), eq(CODE), any()))
                .thenReturn(new CarShareResolutionDto(CAR_ID, "dave"));

        mockMvc.perform(get(BASE + CODE).with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.car_id").value(CAR_ID.toString()))
                .andExpect(jsonPath("$.owner_username").value("dave"));
    }

    /** Only {@code qr} is a scan. Everything else — a channel tag, junk, nothing — is a link. */
    @Test
    void theSourceTagIsParsedAndDefaultsToLink() throws Exception {
        when(garageService.resolveShareCode(any(), any(), any()))
                .thenReturn(new CarShareResolutionDto(CAR_ID, "dave"));

        mockMvc.perform(get(BASE + CODE + "?s=qr").with(TestJwts.user())).andExpect(status().isOk());
        verify(garageService).resolveShareCode(any(), eq(CODE), eq(ShareSource.QR));

        mockMvc.perform(get(BASE + CODE + "?s=wa").with(TestJwts.user())).andExpect(status().isOk());
        mockMvc.perform(get(BASE + CODE + "?s=nonsense").with(TestJwts.user())).andExpect(status().isOk());
        mockMvc.perform(get(BASE + CODE).with(TestJwts.user())).andExpect(status().isOk());
        verify(garageService, org.mockito.Mockito.times(3))
                .resolveShareCode(any(), eq(CODE), eq(ShareSource.LINK));
    }

    /** The code reaches the service untouched — canonicalising it is the service's job, not the wire's. */
    @Test
    void theRawCodeIsPassedThroughUnchanged() throws Exception {
        when(garageService.resolveShareCode(any(), any(), any()))
                .thenReturn(new CarShareResolutionDto(CAR_ID, "dave"));

        mockMvc.perform(get(BASE + "7kq3m9xa2f").with(TestJwts.user())).andExpect(status().isOk());

        verify(garageService).resolveShareCode(any(), eq("7kq3m9xa2f"), any());
    }
}
