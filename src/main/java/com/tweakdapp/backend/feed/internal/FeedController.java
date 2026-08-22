package com.tweakdapp.backend.feed.internal;

import com.tweakdapp.backend.feed.FeedService;
import com.tweakdapp.backend.posts.dto.PostPageDto;
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
     */
    @GetMapping("/global")
    public PostPageDto getGlobalFeed(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "20") int size) {
        return feedService.getGlobalFeed(jwt.getSubject(), cursor, size);
    }
}
