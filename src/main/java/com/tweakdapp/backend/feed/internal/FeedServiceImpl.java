package com.tweakdapp.backend.feed.internal;

import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.feed.FeedService;
import com.tweakdapp.backend.feed.dto.GlobalFeedDto;
import com.tweakdapp.backend.posts.PostsService;
import com.tweakdapp.backend.posts.dto.PostPageDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FeedServiceImpl implements FeedService {

    private final PostsService postsService;
    private final BadgeService badgeService;

    public FeedServiceImpl(PostsService postsService, BadgeService badgeService) {
        this.postsService = postsService;
        this.badgeService = badgeService;
    }

    @Override
    public GlobalFeedDto getGlobalFeed(String currentUserId, String cursor, int size) {
        // The global feed is, today, exactly the posts module's virality-ranked page. Keeping this
        // delegation here (rather than calling posts directly from the controller) gives the
        // personalized feed a home and lets feed-specific policy grow without touching posts.
        PostPageDto page = postsService.getRankedPosts(currentUserId, cursor, size);

        // Badge celebrations ride the first page only. The app fetches page one on launch, which is
        // exactly when it plays the Duolingo-style unlock animation — so folding the list in here
        // spares a dedicated request for something that is almost always empty. Sending it again on
        // page 2+ would replay the animation mid-scroll, so paged requests get an empty list.
        List<UserBadgeDto> pendingCelebrations = cursor == null
                ? badgeService.listPendingCelebrations(UUID.fromString(currentUserId))
                : List.of();

        return new GlobalFeedDto(page.items(), page.nextCursor(), pendingCelebrations);
    }
}
