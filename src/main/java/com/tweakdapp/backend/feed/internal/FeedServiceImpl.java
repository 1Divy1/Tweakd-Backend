package com.tweakdapp.backend.feed.internal;

import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.feed.FeedService;
import com.tweakdapp.backend.feed.dto.GlobalFeedDto;
import com.tweakdapp.backend.posts.PostsService;
import com.tweakdapp.backend.posts.dto.PostPageDto;
import com.tweakdapp.backend.relationships.RelationshipService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class FeedServiceImpl implements FeedService {

    private final PostsService postsService;
    private final BadgeService badgeService;
    private final RelationshipService relationshipService;

    public FeedServiceImpl(PostsService postsService, BadgeService badgeService,
                           RelationshipService relationshipService) {
        this.postsService = postsService;
        this.badgeService = badgeService;
        this.relationshipService = relationshipService;
    }

    @Override
    public GlobalFeedDto getGlobalFeed(String currentUserId, String cursor, int size) {
        // The global feed is the posts module's virality-ranked page, with one piece of feed policy
        // folded in: what the viewer's followees reposted ranks as fresh as the repost. Who someone
        // follows is the relationships module's to answer, so it is looked up here and handed to
        // posts as plain ids — posts never reads the follows table.
        List<UUID> followeeIds = relationshipService.findFollowingIds(UUID.fromString(currentUserId));
        PostPageDto page = postsService.getRankedPosts(currentUserId, followeeIds, cursor, size);

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
