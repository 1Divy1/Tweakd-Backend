package com.carsocialmedia.backend.feed.internal;

import com.carsocialmedia.backend.feed.FeedService;
import com.carsocialmedia.backend.posts.PostsService;
import com.carsocialmedia.backend.posts.dto.PostPageDto;
import org.springframework.stereotype.Service;

@Service
public class FeedServiceImpl implements FeedService {

    private final PostsService postsService;

    public FeedServiceImpl(PostsService postsService) {
        this.postsService = postsService;
    }

    @Override
    public PostPageDto getGlobalFeed(String currentUserId, String cursor, int size) {
        // The global feed is, today, exactly the posts module's virality-ranked page. Keeping this
        // delegation here (rather than calling posts directly from the controller) gives the
        // personalized feed a home and lets feed-specific policy grow without touching posts.
        return postsService.getRankedPosts(currentUserId, cursor, size);
    }
}
