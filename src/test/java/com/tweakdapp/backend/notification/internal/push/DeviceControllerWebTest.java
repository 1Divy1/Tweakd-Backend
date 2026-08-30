package com.tweakdapp.backend.notification.internal.push;

import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link DeviceController}: authentication, the JWT-derived account (never the
 * body), request validation, and the 204-always contract on unregister.
 */
@AppWebMvcTest(DeviceController.class)
class DeviceControllerWebTest {

    private static final String TOKEN = "d".repeat(64) + ":APA91bTESTTOKEN";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PushDeviceService pushDeviceService;

    private static String body(String token, String platform) {
        return """
                {"token":"%s","platform":"%s","app_version":"1.0.0+1","locale":"en"}
                """.formatted(token, platform);
    }

    // ---- authentication -----------------------------------------------------

    @Test
    void registeringRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TOKEN, "ios")))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(pushDeviceService);
    }

    @Test
    void unregisteringRequiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(pushDeviceService);
    }

    // ---- registration -------------------------------------------------------

    @Test
    void registeringADeviceReturns200AndUsesTheJwtSubjectAsTheAccount() throws Exception {
        UUID caller = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

        mockMvc.perform(post("/api/v1/notifications/devices")
                        .with(TestJwts.user(caller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TOKEN, "ios")))
                .andExpect(status().isOk());

        verify(pushDeviceService).register(eq(caller), any(DeviceRegistrationRequest.class));
    }

    @Test
    void anUnknownPlatformIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/devices")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TOKEN, "windows")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pushDeviceService);
    }

    @Test
    void aShortTokenIsRejectedBeforeItReachesTheDatabase() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/devices")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("tooshort", "ios")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pushDeviceService);
    }

    @Test
    void aMissingTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/devices")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"platform\":\"ios\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pushDeviceService);
    }

    @Test
    void aMalformedLocaleIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/devices")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","platform":"ios","locale":"not a locale!"}
                                """.formatted(TOKEN)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pushDeviceService);
    }

    // ---- unregistration -----------------------------------------------------

    @Test
    void unregisteringReturns204AndIsScopedToTheCaller() throws Exception {
        UUID caller = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

        mockMvc.perform(delete("/api/v1/notifications/devices")
                        .with(TestJwts.user(caller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isNoContent());

        verify(pushDeviceService).unregister(caller, TOKEN);
    }

    /**
     * Unregister is idempotent and must not reveal whether the token existed — an unknown token is
     * still a 204, so the endpoint cannot be used as a token-existence oracle.
     */
    @Test
    void unregisteringAnUnknownTokenStillReturns204() throws Exception {
        String unknown = "z".repeat(80);

        mockMvc.perform(delete("/api/v1/notifications/devices")
                        .with(TestJwts.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + unknown + "\"}"))
                .andExpect(status().isNoContent());

        verify(pushDeviceService).unregister(TestJwts.USER_ID, unknown);
    }
}
