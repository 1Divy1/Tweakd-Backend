package com.tweakdapp.backend.feedbackfeed.internal;

import com.tweakdapp.backend.feedbackfeed.FeedbackFeedService;
import com.tweakdapp.backend.feedbackfeed.dto.FeedbackAuthorDto;
import com.tweakdapp.backend.feedbackfeed.dto.FeedbackCategoryDto;
import com.tweakdapp.backend.feedbackfeed.dto.FeedbackFeedPageDto;
import com.tweakdapp.backend.feedbackfeed.dto.FeedbackFeedStatusDto;
import com.tweakdapp.backend.feedbackfeed.dto.FeedbackMessageDto;
import com.tweakdapp.backend.feedbackfeed.dto.request.SubmitFeedbackMessageRequest;
import com.tweakdapp.backend.feedbackfeed.exception.FeedbackDeletionClosedException;
import com.tweakdapp.backend.feedbackfeed.exception.FeedbackMessageNotFoundException;
import com.tweakdapp.backend.feedbackfeed.exception.FeedbackVotingClosedException;
import com.tweakdapp.backend.feedbackfeed.exception.InvalidFeedbackFeedCursorException;
import com.tweakdapp.backend.feedbackfeed.exception.NotFeedbackAuthorException;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST surface of {@link FeedbackFeedController}: the auth requirement, the exact snake_case
 * JSON the mobile app parses (a rename here is a silent client break), query-parameter defaults,
 * and the exception→status mapping.
 */
@AppWebMvcTest(FeedbackFeedController.class)
class FeedbackFeedControllerWebTest {

    private static final UUID MESSAGE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedbackFeedService feedbackFeedService;

    // ---- auth ---------------------------------------------------------------

    @Test
    void theFeedRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/feedback-feed"))
                .andExpect(status().isUnauthorized());
    }

    // ---- the card's wire format ---------------------------------------------

    @Test
    void aCardSerializesEveryFieldInSnakeCase() throws Exception {
        when(feedbackFeedService.getMessage(any(), eq(MESSAGE_ID))).thenReturn(card(1, "We shipped it."));

        mockMvc.perform(get("/api/v1/feedback-feed/{id}", MESSAGE_ID).with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MESSAGE_ID.toString()))
                .andExpect(jsonPath("$.message").value("Cover photo resets when I reorder the gallery."))
                .andExpect(jsonPath("$.staff_response").value("We shipped it."))
                .andExpect(jsonPath("$.author.username").value("wheelwell_dana"))
                .andExpect(jsonPath("$.author.name").value("Dana Wheelwell"))
                .andExpect(jsonPath("$.author.avatar_url").value("https://cdn/a.png"))
                .andExpect(jsonPath("$.type.id").value("bug"))
                .andExpect(jsonPath("$.type.label").value("Bug"))
                .andExpect(jsonPath("$.status.id").value("under_development"))
                .andExpect(jsonPath("$.up_votes").value(512))
                .andExpect(jsonPath("$.down_votes").value(37))
                .andExpect(jsonPath("$.net_votes").value(475))
                .andExpect(jsonPath("$.my_vote").value(1))
                .andExpect(jsonPath("$.viewer_is_author").value(false))
                .andExpect(jsonPath("$.created_at").exists())
                .andExpect(jsonPath("$.completed_at").exists());
    }

    @Test
    void anUnvotedCardReportsANullVoteRatherThanZero() throws Exception {
        // 0 would be indistinguishable from a real vote value in a client's truthiness check.
        when(feedbackFeedService.getMessage(any(), eq(MESSAGE_ID))).thenReturn(card(null, null));

        mockMvc.perform(get("/api/v1/feedback-feed/{id}", MESSAGE_ID).with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.my_vote").value(nullValue()))
                .andExpect(jsonPath("$.staff_response").value(nullValue()));
    }

    // ---- the feed -----------------------------------------------------------

    @Test
    void theFeedDefaultsToNewestAndTwentyPerPage() throws Exception {
        when(feedbackFeedService.getFeed(any(), any(), any(), anyInt()))
                .thenReturn(new FeedbackFeedPageDto(List.of(card(null, null)), "eyJ0"));

        mockMvc.perform(get("/api/v1/feedback-feed").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(MESSAGE_ID.toString()))
                .andExpect(jsonPath("$.next_cursor").value("eyJ0"));

        verify(feedbackFeedService).getFeed(eq(TestJwts.USER_ID), eq("newest"), eq(null), eq(20));
    }

    @Test
    void theFeedPassesSortCursorAndSizeThrough() throws Exception {
        when(feedbackFeedService.getFeed(any(), any(), any(), anyInt()))
                .thenReturn(new FeedbackFeedPageDto(List.of(), null));

        mockMvc.perform(get("/api/v1/feedback-feed")
                        .param("sort", "popular")
                        .param("cursor", "abc")
                        .param("size", "5")
                        .with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next_cursor").value(nullValue()));

        verify(feedbackFeedService).getFeed(eq(TestJwts.USER_ID), eq("popular"), eq("abc"), eq(5));
    }

    @Test
    void theCompletedSectionIsItsOwnRouteAndIsNotSwallowedByTheIdPattern() throws Exception {
        when(feedbackFeedService.getCompleted(any(), any(), anyInt()))
                .thenReturn(new FeedbackFeedPageDto(List.of(), null));

        mockMvc.perform(get("/api/v1/feedback-feed/completed").with(TestJwts.user()))
                .andExpect(status().isOk());

        verify(feedbackFeedService).getCompleted(eq(TestJwts.USER_ID), eq(null), eq(20));
        verify(feedbackFeedService, org.mockito.Mockito.never()).getMessage(any(), any());
    }

    @Test
    void anUndecodableCursorIsABadRequest() throws Exception {
        when(feedbackFeedService.getFeed(any(), any(), any(), anyInt()))
                .thenThrow(new InvalidFeedbackFeedCursorException("nope"));

        mockMvc.perform(get("/api/v1/feedback-feed").param("cursor", "nope").with(TestJwts.user()))
                .andExpect(status().isBadRequest());
    }

    // ---- publishing ---------------------------------------------------------

    @Test
    void publishingReturns201AndTheNewCard() throws Exception {
        when(feedbackFeedService.submit(any(), any())).thenReturn(card(null, null));

        mockMvc.perform(post("/api/v1/feedback-feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"bug\",\"message\":\"Cover photo resets.\"}")
                        .with(TestJwts.user()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(MESSAGE_ID.toString()));

        verify(feedbackFeedService).submit(eq(TestJwts.USER_ID),
                eq(new SubmitFeedbackMessageRequest("bug", "Cover photo resets.")));
    }

    @Test
    void anEmptyMessageIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/feedback-feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"bug\",\"message\":\"   \"}")
                        .with(TestJwts.user()))
                .andExpect(status().isBadRequest());

        verify(feedbackFeedService, org.mockito.Mockito.never()).submit(any(), any());
    }

    @Test
    void aMessageOverFiveHundredCharactersIsRejected() throws Exception {
        String tooLong = "x".repeat(501);

        mockMvc.perform(post("/api/v1/feedback-feed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"bug\",\"message\":\"" + tooLong + "\"}")
                        .with(TestJwts.user()))
                .andExpect(status().isBadRequest());

        verify(feedbackFeedService, org.mockito.Mockito.never()).submit(any(), any());
    }

    @Test
    void deletingSomebodyElsesMessageIsForbidden() throws Exception {
        doThrow(new NotFeedbackAuthorException())
                .when(feedbackFeedService).deleteOwn(any(), eq(MESSAGE_ID));

        mockMvc.perform(delete("/api/v1/feedback-feed/{id}", MESSAGE_ID).with(TestJwts.user()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletingOwnMessageReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/feedback-feed/{id}", MESSAGE_ID).with(TestJwts.user()))
                .andExpect(status().isNoContent());

        verify(feedbackFeedService).deleteOwn(TestJwts.USER_ID, MESSAGE_ID);
    }

    @Test
    void deletingAMessageAlreadyOnTheRoadmapIs409() throws Exception {
        doThrow(new FeedbackDeletionClosedException())
                .when(feedbackFeedService).deleteOwn(any(), eq(MESSAGE_ID));

        mockMvc.perform(delete("/api/v1/feedback-feed/{id}", MESSAGE_ID).with(TestJwts.user()))
                .andExpect(status().isConflict());
    }

    @Test
    void anUnknownMessageIs404() throws Exception {
        when(feedbackFeedService.getMessage(any(), eq(MESSAGE_ID)))
                .thenThrow(new FeedbackMessageNotFoundException(MESSAGE_ID));

        mockMvc.perform(get("/api/v1/feedback-feed/{id}", MESSAGE_ID).with(TestJwts.user()))
                .andExpect(status().isNotFound());
    }

    // ---- voting -------------------------------------------------------------

    @Test
    void votingReturnsTheUpdatedCardSoTheClientNeedNotRefetch() throws Exception {
        when(feedbackFeedService.vote(any(), eq(MESSAGE_ID), eq(-1))).thenReturn(card(-1, null));

        mockMvc.perform(post("/api/v1/feedback-feed/{id}/vote", MESSAGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":-1}")
                        .with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.my_vote").value(-1))
                .andExpect(jsonPath("$.net_votes").value(475));
    }

    @Test
    void aVoteWithoutAValueIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/feedback-feed/{id}/vote", MESSAGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(TestJwts.user()))
                .andExpect(status().isBadRequest());

        verify(feedbackFeedService, org.mockito.Mockito.never()).vote(any(), any(), anyInt());
    }

    @Test
    void votingOnACompletedMessageIsAConflict() throws Exception {
        when(feedbackFeedService.vote(any(), eq(MESSAGE_ID), anyInt()))
                .thenThrow(new FeedbackVotingClosedException());

        mockMvc.perform(post("/api/v1/feedback-feed/{id}/vote", MESSAGE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":1}")
                        .with(TestJwts.user()))
                .andExpect(status().isConflict());
    }

    @Test
    void withdrawingAVoteReturnsTheUpdatedCard() throws Exception {
        when(feedbackFeedService.removeVote(any(), eq(MESSAGE_ID))).thenReturn(card(null, null));

        mockMvc.perform(delete("/api/v1/feedback-feed/{id}/vote", MESSAGE_ID).with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.my_vote").value(nullValue()));
    }

    // ---- reference data -----------------------------------------------------

    @Test
    void referenceDataIsExposedForThePickers() throws Exception {
        when(feedbackFeedService.listTypes())
                .thenReturn(List.of(new FeedbackCategoryDto("bug", "Bug")));
        when(feedbackFeedService.listStatuses())
                .thenReturn(List.of(new FeedbackFeedStatusDto("sent", "Sent")));

        mockMvc.perform(get("/api/v1/feedback-feed/types").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("bug"))
                .andExpect(jsonPath("$[0].label").value("Bug"));

        mockMvc.perform(get("/api/v1/feedback-feed/statuses").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("sent"));
    }

    // ---- helpers ------------------------------------------------------------

    private static FeedbackMessageDto card(Integer myVote, String staffResponse) {
        return new FeedbackMessageDto(
                MESSAGE_ID,
                new FeedbackAuthorDto(AUTHOR_ID, "Dana Wheelwell", "wheelwell_dana", "https://cdn/a.png"),
                "Cover photo resets when I reorder the gallery.",
                new FeedbackCategoryDto("bug", "Bug"),
                new FeedbackFeedStatusDto("under_development", "Under development"),
                staffResponse,
                512, 37, 475,
                myVote,
                false,
                false,
                Instant.parse("2026-08-15T09:41:00Z"),
                Instant.parse("2026-08-16T12:00:00Z"));
    }
}
