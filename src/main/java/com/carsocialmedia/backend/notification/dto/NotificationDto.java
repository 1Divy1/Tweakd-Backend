package com.carsocialmedia.backend.notification.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One in-app notification. {@code type} discriminates the producer ({@code feedback_status},
 * {@code ticket_reply}, {@code moderation_warning}, {@code content_removed}, ...) and {@code payload}
 * carries the type-specific ids the client needs to deep-link (e.g. {@code feedbackId}).
 */
public record NotificationDto(
        UUID id,
        String type,
        String title,
        String body,
        Map<String, Object> payload,
        boolean read,
        Instant createdAt
) {}
