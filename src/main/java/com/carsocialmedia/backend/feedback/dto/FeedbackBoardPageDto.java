package com.carsocialmedia.backend.feedback.dto;

import java.util.List;

/**
 * One keyset page of the public feedback board. The client echoes {@code nextCursor} back as
 * {@code ?cursor=}; {@code null} means this was the last page.
 */
public record FeedbackBoardPageDto(
        List<FeedbackBoardItemDto> items,
        String nextCursor
) {}
