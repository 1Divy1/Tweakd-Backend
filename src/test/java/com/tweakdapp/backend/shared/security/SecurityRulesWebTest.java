package com.tweakdapp.backend.shared.security;

import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The route-level authorization rules of {@link SecurityConfig}, exercised over MockMvc with a
 * throwaway probe controller: everything requires authentication, {@code /api/v1/admin/**}
 * additionally requires ROLE_ADMIN, the {@code /ws} handshake is open.
 */
@AppWebMvcTest(SecurityRulesWebTest.ProbeController.class)
@Import(SecurityRulesWebTest.ProbeController.class)
class SecurityRulesWebTest {

    @Autowired
    private MockMvc mockMvc;

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/probe")
        String probe(@AuthenticationPrincipal Jwt jwt) {
            return jwt.getSubject();
        }

        @GetMapping("/api/v1/admin/probe")
        String adminProbe() {
            return "admin-ok";
        }
    }

    @Test
    void unauthenticatedRequestGets401() throws Exception {
        mockMvc.perform(get("/api/v1/probe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRequestPassesAndControllerSeesTheJwtSubject() throws Exception {
        mockMvc.perform(get("/api/v1/probe").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(content().string(TestJwts.USER_ID.toString()));
    }

    @Test
    void adminRoutesRejectRegularUsersWith403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe").with(TestJwts.user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoutesAllowAdmins() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe").with(TestJwts.admin()))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-ok"));
    }

    @Test
    void websocketHandshakePathIsNotBlockedBySecurity() throws Exception {
        // No STOMP endpoint is mounted in this slice — the assertion is only that security
        // lets the request through (404 from MVC, not 401 from the filter chain).
        int status = mockMvc.perform(get("/ws/info"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isNotIn(401, 403);
    }
}
