package com.tweakdapp.backend.feedback.dto;

import java.util.List;
import java.util.UUID;

/**
 * Result of an admin status change, with everything the caller needs to notify interested users:
 * {@code recipients} is the union of the feedback's author and its subscribers (de-duplicated).
 * The {@code admin} module turns this into in-app notifications — the {@code feedback} module
 * deliberately does not depend on {@code notification}.
 */
public record FeedbackStatusChangeDto(
        UUID feedbackId,
        String contentPreview,
        FeedbackStatusDto newStatus,
        List<UUID> recipients
) {}
