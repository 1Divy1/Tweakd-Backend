package com.tweakdapp.backend.feed.internal;

import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.dto.BadgeDto;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.feed.dto.GlobalFeedDto;
import com.tweakdapp.backend.posts.PostsService;
import com.tweakdapp.backend.posts.dto.PostPageDto;
import com.tweakdapp.backend.relationships.RelationshipService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The feed-policy {@link FeedServiceImpl} owns: it is the posts module's ranked page — shaped by
 * who the caller follows, whose reposts rank as fresh — plus the caller's pending badge
 * celebrations folded into the <em>first</em> page only — the app fetches that on launch, which is
 * when it animates them, so no dedicated request is needed and a scroll to page two never replays
 * an animation.
 */
@ExtendWith(MockitoExtension.class)
class FeedServiceImplTest {

    private static final String USER = "00000000-0000-0000-0000-0000000000a1";

    @Mock private PostsService postsService;
    @Mock private BadgeService badgeService;
    @Mock private RelationshipService relationshipService;

    private static UserBadgeDto pioneer() {
        return new UserBadgeDto(
                new BadgeDto("pioneer", "Pioneer", "One of the first.",
                        "https://assets/badges/pioneer/badge-unlocked.svg",
                        "https://assets/badges/pioneer/badge-locked.svg", true,
                        "account_created", null, null,
                        Instant.parse("2026-09-03T16:22:34Z")),
                Instant.parse("2026-09-03T17:00:00Z"));
    }

    @Test
    void theFirstPageCarriesTheCallersPendingCelebrations() {
        FeedServiceImpl service = new FeedServiceImpl(postsService, badgeService, relationshipService);
        when(postsService.getRankedPosts(eq(USER), any(), isNull(), anyInt()))
                .thenReturn(new PostPageDto(List.of(), "cursor-2"));
        when(badgeService.listPendingCelebrations(UUID.fromString(USER)))
                .thenReturn(List.of(pioneer()));

        GlobalFeedDto feed = service.getGlobalFeed(USER, null, 20);

        assertThat(feed.nextCursor()).isEqualTo("cursor-2");
        assertThat(feed.pendingBadgeCelebrations()).extracting(ub -> ub.badge().id()).containsExactly("pioneer");
    }

    @Test
    void aPagedRequestNeverTouchesBadgesAndReturnsAnEmptyList() {
        FeedServiceImpl service = new FeedServiceImpl(postsService, badgeService, relationshipService);
        when(postsService.getRankedPosts(eq(USER), any(), eq("cursor-2"), anyInt()))
                .thenReturn(new PostPageDto(List.of(), null));

        GlobalFeedDto feed = service.getGlobalFeed(USER, "cursor-2", 20);

        assertThat(feed.pendingBadgeCelebrations()).isEmpty();
        verifyNoInteractions(badgeService);
    }

    @Test
    void theRankedPageIsPassedThroughUnchanged() {
        FeedServiceImpl service = new FeedServiceImpl(postsService, badgeService, relationshipService);
        PostPageDto page = new PostPageDto(List.of(), "next");
        when(postsService.getRankedPosts(any(), any(), any(), anyInt())).thenReturn(page);
        when(badgeService.listPendingCelebrations(any())).thenReturn(List.of());

        GlobalFeedDto feed = service.getGlobalFeed(USER, null, 20);

        assertThat(feed.items()).isSameAs(page.items());
        assertThat(feed.nextCursor()).isEqualTo("next");
        verify(badgeService).listPendingCelebrations(UUID.fromString(USER));
        verify(postsService, never()).getRankedPosts(eq(USER), any(), eq("x"), anyInt());
    }

    @Test
    void theCallersFolloweesShapeTheRankedPage() {
        FeedServiceImpl service = new FeedServiceImpl(postsService, badgeService, relationshipService);
        List<UUID> followees = List.of(UUID.fromString("00000000-0000-0000-0000-0000000000b1"));
        when(relationshipService.findFollowingIds(UUID.fromString(USER))).thenReturn(followees);
        when(postsService.getRankedPosts(eq(USER), eq(followees), eq("cursor-2"), anyInt()))
                .thenReturn(new PostPageDto(List.of(), null));

        service.getGlobalFeed(USER, "cursor-2", 20);

        verify(postsService).getRankedPosts(USER, followees, "cursor-2", 20);
    }
}
