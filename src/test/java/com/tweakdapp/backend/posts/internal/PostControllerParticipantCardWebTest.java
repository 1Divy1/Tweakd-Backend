package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.posts.PostsService;
import com.tweakdapp.backend.posts.exception.ParticipantCardCooldownException;
import com.tweakdapp.backend.posts.exception.ParticipantCardNotFoundException;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The share-a-card endpoint's wire contract — in particular the cooldown refusal, whose
 * {@code error} and {@code details.next_post_allowed_at} are exactly what the app reads to say when
 * the card can go out again.
 */
@AppWebMvcTest(PostController.class)
class PostControllerParticipantCardWebTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CAR = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final String BODY = "{\"event_id\":\"" + EVENT + "\",\"car_id\":\"" + CAR + "\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostsService postsService;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/posts/participant-card")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aCooldownRefusalTellsTheAppWhenTheCardReopens() throws Exception {
        when(postsService.shareParticipantCard(eq(USER.toString()), any()))
                .thenThrow(new ParticipantCardCooldownException(Instant.parse("2026-09-13T15:40:00Z")));

        mockMvc.perform(post("/api/v1/posts/participant-card").with(TestJwts.user(USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("participant_card_cooldown"))
                .andExpect(jsonPath("$.details.next_post_allowed_at").value("2026-09-13T15:40:00Z"));
    }

    @Test
    void aCardThatIsNotYoursIsANotFound() throws Exception {
        when(postsService.shareParticipantCard(eq(USER.toString()), any()))
                .thenThrow(new ParticipantCardNotFoundException(EVENT, CAR));

        mockMvc.perform(post("/api/v1/posts/participant-card").with(TestJwts.user(USER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void theRequestMustNameBothHalvesOfTheCard() throws Exception {
        mockMvc.perform(post("/api/v1/posts/participant-card").with(TestJwts.user(USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"event_id\":\"" + EVENT + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
