package com.carsocialmedia.backend.feedback.dto;

import java.util.List;

/**
 * One keyset page of a feedback's comments, newest first. {@code nextCursor} is echoed back as
 * {@code ?cursor=}; {@code null} means last page.
 */
public record FeedbackCommentPageDto(
        List<FeedbackCommentDto> items,
        String nextCursor
) {}
