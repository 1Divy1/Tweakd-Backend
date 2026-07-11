package com.carsocialmedia.backend.notification.dto;

import java.util.List;

/**
 * One keyset page of a user's notifications, newest first. The client echoes {@code nextCursor} back
 * as {@code ?cursor=} for the next page; {@code null} means this was the last page.
 */
public record NotificationPageDto(
        List<NotificationDto> items,
        String nextCursor
) {}
