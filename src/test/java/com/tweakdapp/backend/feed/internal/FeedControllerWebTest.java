package com.tweakdapp.backend.feed.internal;

import com.tweakdapp.backend.badges.dto.BadgeDto;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.feed.FeedService;
import com.tweakdapp.backend.feed.dto.GlobalFeedDto;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST surface of {@link FeedController}: the auth requirement, the snake_case JSON the app
 * parses, the query-parameter defaults, and that {@code pending_badge_celebrations} is present in
 * the envelope alongside the unchanged {@code items} / {@code next_cursor}.
 */
@AppWebMvcTest(FeedController.class)
class FeedControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeedService feedService;

    private static UserBadgeDto pioneer() {
        return new UserBadgeDto(
                new BadgeDto("pioneer", "Pioneer", "One of the first.",
                        "https://assets.tweakd.app/badges/pioneer/badge-unlocked.svg",
                        "https://assets.tweakd.app/badges/pioneer/badge-locked.svg", true,
                        Instant.parse("2026-09-03T16:22:34Z")),
                Instant.parse("2026-09-03T17:00:00Z"));
    }

    @Test
    void theFeedRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/feed/global")).andExpect(status().isUnauthorized());
    }

    @Test
    void theFirstPageSerializesTheCelebrationsAlongsideTheUnchangedPageEnvelope() throws Exception {
        when(feedService.getGlobalFeed(eq(TestJwts.USER_ID.toString()), isNull(), eq(20)))
                .thenReturn(new GlobalFeedDto(List.of(), "cursor-2", List.of(pioneer())));

        mockMvc.perform(get("/api/v1/feed/global").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.next_cursor").value("cursor-2"))
                .andExpect(jsonPath("$.pending_badge_celebrations", hasSize(1)))
                .andExpect(jsonPath("$.pending_badge_celebrations[0].badge.id").value("pioneer"))
                .andExpect(jsonPath("$.pending_badge_celebrations[0].badge.unlocked_url")
                        .value("https://assets.tweakd.app/badges/pioneer/badge-unlocked.svg"))
                .andExpect(jsonPath("$.pending_badge_celebrations[0].earned_at").exists());
    }

    /** A paged request passes the cursor straight through; the app sees an empty celebrations list. */
    @Test
    void aPagedRequestForwardsTheCursorAndCarriesNoCelebrations() throws Exception {
        when(feedService.getGlobalFeed(eq(TestJwts.USER_ID.toString()), eq("cursor-2"), eq(20)))
                .thenReturn(new GlobalFeedDto(List.of(), null, List.of()));

        mockMvc.perform(get("/api/v1/feed/global").param("cursor", "cursor-2").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.next_cursor").value(nullValue()))
                .andExpect(jsonPath("$.pending_badge_celebrations", hasSize(0)));

        verify(feedService).getGlobalFeed(TestJwts.USER_ID.toString(), "cursor-2", 20);
    }
}
