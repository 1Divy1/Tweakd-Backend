package com.tweakdapp.backend.notification.internal;

import com.tweakdapp.backend.notification.NotificationService;
import com.tweakdapp.backend.notification.dto.NotificationDto;
import com.tweakdapp.backend.notification.dto.NotificationPageDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * The user-facing notification endpoints: the notification feed, the unread badge count, and the two
 * mark-as-read operations. Everything is scoped to the JWT subject — notifications are only ever
 * written by other modules through {@link NotificationService}.
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationService notificationService;

    NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** One keyset page of the caller's notifications, newest first. */
    @GetMapping
    public NotificationPageDto getNotifications(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam(required = false) String cursor,
                                                @RequestParam(defaultValue = "20") int size) {
        return notificationService.listNotifications(UUID.fromString(jwt.getSubject()), cursor, size);
    }

    /** Unread count for the app's notification badge. */
    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("unread", notificationService.countUnread(UUID.fromString(jwt.getSubject())));
    }

    /** Marks one notification as read and returns it. */
    @PostMapping("/{notificationId}/read")
    public NotificationDto markRead(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID notificationId) {
        return notificationService.markRead(UUID.fromString(jwt.getSubject()), notificationId);
    }

    /** Marks every unread notification as read; returns how many were affected. */
    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("marked", notificationService.markAllRead(UUID.fromString(jwt.getSubject())));
    }
}
