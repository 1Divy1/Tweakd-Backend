package com.carsocialmedia.backend.admin.internal;

import com.carsocialmedia.backend.feedback.FeedbackService;
import com.carsocialmedia.backend.feedback.dto.FeedbackStatusChangeDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackStatusDto;
import com.carsocialmedia.backend.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * The feedback status changes' notification fan-out. This is why the orchestration lives here: the
 * feedback module reports who to notify but deliberately does not depend on {@code notification},
 * so the admin module bridges the two. Reads and the plain response write pass straight through to
 * {@link FeedbackService} from the controller.
 */
@Service
public class AdminFeedbackService {

    private final FeedbackService feedbackService;
    private final NotificationService notificationService;

    AdminFeedbackService(FeedbackService feedbackService, NotificationService notificationService) {
        this.feedbackService = feedbackService;
        this.notificationService = notificationService;
    }

    /**
     * Moves the feedback through its lifecycle and notifies the author + every subscriber in-app
     * ("Under review" → "In development" → "Testing" → "Released"...). Returns the new status.
     */
    @Transactional
    public FeedbackStatusDto updateStatus(UUID feedbackId, String statusId) {
        FeedbackStatusChangeDto change = feedbackService.updateStatus(feedbackId, statusId);
        notificationService.pushToAll(
                change.recipients(),
                "feedback_status",
                "Feedback update: " + change.newStatus().name(),
                "\"" + change.contentPreview() + "\" is now " + change.newStatus().name() + ".",
                Map.of("feedbackId", change.feedbackId().toString(), "status", change.newStatus().id()));
        return change.newStatus();
    }
}
