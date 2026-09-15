package com.tweakdapp.backend.relationships.internal;

import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.relationships.BlockService;
import com.tweakdapp.backend.relationships.dto.BlockedAccountDto;
import com.tweakdapp.backend.relationships.exception.CannotBlockSelfException;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
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
 * The REST surface of {@link BlockController}: auth requirement, 204s on block/unblock with
 * JWT-subject delegation, the snake_case list shape, and the exception→status mapping (self-block
 * 400, unknown username 404).
 */
@AppWebMvcTest(BlockController.class)
class BlockControllerWebTest {

    private static final String SUBJECT = TestJwts.USER_ID.toString();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlockService blockService;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/blocks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsBlockedAccountsInSnakeCase() throws Exception {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(blockService.listBlocked(SUBJECT)).thenReturn(List.of(
                new BlockedAccountDto(id, "Racer", "racer", "http://a/x.png", Instant.parse("2026-09-14T10:00:00Z"))));

        mockMvc.perform(get("/api/v1/blocks").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].username").value("racer"))
                .andExpect(jsonPath("$[0].avatar_url").value("http://a/x.png"))
                .andExpect(jsonPath("$[0].blocked_at").exists());
    }

    @Test
    void blockReturns204AndDelegatesTheJwtSubject() throws Exception {
        mockMvc.perform(post("/api/v1/blocks/{username}", "racer").with(TestJwts.user()))
                .andExpect(status().isNoContent());

        verify(blockService).block(SUBJECT, "racer");
    }

    @Test
    void blockingYourselfSurfacesAs400() throws Exception {
        doThrow(new CannotBlockSelfException()).when(blockService).block(SUBJECT, "me");

        mockMvc.perform(post("/api/v1/blocks/{username}", "me").with(TestJwts.user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void blockingAnUnknownUserSurfacesAs404() throws Exception {
        doThrow(ProfileNotFoundException.byUsername("ghost")).when(blockService).block(SUBJECT, "ghost");

        mockMvc.perform(post("/api/v1/blocks/{username}", "ghost").with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unblockReturns204AndDelegates() throws Exception {
        mockMvc.perform(delete("/api/v1/blocks/{username}", "racer").with(TestJwts.user()))
                .andExpect(status().isNoContent());

        verify(blockService).unblock(SUBJECT, "racer");
    }
}
