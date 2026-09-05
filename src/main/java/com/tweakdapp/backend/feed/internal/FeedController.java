package com.tweakdapp.backend.feed.internal;

import com.tweakdapp.backend.feed.FeedService;
import com.tweakdapp.backend.feed.dto.GlobalFeedDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    /**
     * One keyset page of the global feed (most viral posts across the app), highest ranked first.
     * The client passes the {@code nextCursor} from the previous response back as {@code ?cursor=}
     * to page on.
     *
     * <p>The first page also carries {@code pending_badge_celebrations} — the badges the caller has
     * earned but not yet seen animated. The app fetches this on launch and plays the animation for
     * each, then acknowledges them via {@code POST /api/v1/badges/me/pending-celebration/{badgeId}}.
     * Paged requests ({@code ?cursor=}) return an empty list there.
     */
    @GetMapping("/global")
    public GlobalFeedDto getGlobalFeed(@AuthenticationPrincipal Jwt jwt,
                                       @RequestParam(required = false) String cursor,
                                       @RequestParam(defaultValue = "20") int size) {
        return feedService.getGlobalFeed(jwt.getSubject(), cursor, size);
    }
}
