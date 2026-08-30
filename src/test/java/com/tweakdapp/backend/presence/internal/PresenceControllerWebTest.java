package com.tweakdapp.backend.presence.internal;

import com.tweakdapp.backend.presence.PresenceService;
import com.tweakdapp.backend.presence.dto.PresenceDto;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The presence REST slice: authentication is required (401), the comma-separated {@code user_ids}
 * param is parsed and delegated, results serialize as a snake_case array ({@code user_id},
 * {@code online}, {@code last_seen_at}), and the batch cap (>100 ids) and a missing param both
 * short-circuit to 400 without reaching the service.
 */
@AppWebMvcTest(PresenceController.class)
class PresenceControllerWebTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final Instant LAST_SEEN = Instant.parse("2026-07-16T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PresenceService presenceService;

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/presence").param("user_ids", A.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsOnlinePresenceInSnakeCase() throws Exception {
        when(presenceService.getPresence(any())).thenReturn(Map.of(A, PresenceDto.online(A)));

        mockMvc.perform(get("/api/v1/presence").param("user_ids", A.toString()).with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user_id").value(A.toString()))
                .andExpect(jsonPath("$[0].online").value(true));
    }

    @Test
    void returnsOfflinePresenceWithLastSeenTimestamp() throws Exception {
        when(presenceService.getPresence(any())).thenReturn(Map.of(A, PresenceDto.offline(A, LAST_SEEN)));

        mockMvc.perform(get("/api/v1/presence").param("user_ids", A.toString()).with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].user_id").value(A.toString()))
                .andExpect(jsonPath("$[0].online").value(false))
                .andExpect(jsonPath("$[0].last_seen_at").value(startsWith("2026-07-16T10:00:00")));
    }

    @Test
    void parsesCommaSeparatedIdsAndDelegatesThemToTheService() throws Exception {
        when(presenceService.getPresence(any())).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/presence").param("user_ids", A + "," + B).with(TestJwts.user()))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(presenceService).getPresence(captor.capture());
        assertThat(captor.getValue()).containsExactly(A, B);
    }

    @Test
    void rejectsMoreThanOneHundredIdsWithoutCallingTheService() throws Exception {
        String ids = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> new UUID(0, i).toString())
                .collect(Collectors.joining(","));

        mockMvc.perform(get("/api/v1/presence").param("user_ids", ids).with(TestJwts.user()))
                .andExpect(status().isBadRequest());

        verify(presenceService, never()).getPresence(any());
    }

    @Test
    void missingUserIdsParamIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/presence").with(TestJwts.user()))
                .andExpect(status().isBadRequest());
    }
}
