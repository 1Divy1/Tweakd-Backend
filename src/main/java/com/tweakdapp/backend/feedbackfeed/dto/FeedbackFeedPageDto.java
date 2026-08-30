package com.tweakdapp.backend.feedbackfeed.dto;

import java.util.List;

/**
 * One keyset page of feed items. The client echoes {@code nextCursor} back as {@code ?cursor=} to
 * fetch the next page; a {@code null} cursor means this was the last page.
 *
 * <p>The token is opaque and <strong>tied to the sort it came from</strong> — it encodes that
 * sort's key alongside the tiebreaker id, so replaying a "popular" cursor against "newest" is
 * rejected rather than silently returning nonsense.
 *
 * @param items      the items in this page
 * @param nextCursor opaque token for the next page, or {@code null} if this is the last page
 */
public record FeedbackFeedPageDto(
        List<FeedbackMessageDto> items,
        String nextCursor
) {}
