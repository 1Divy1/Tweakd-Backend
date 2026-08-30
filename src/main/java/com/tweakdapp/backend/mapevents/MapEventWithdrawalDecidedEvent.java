package com.tweakdapp.backend.mapevents;

import java.util.UUID;

/**
 * Published when an organizer decides a participant's withdrawal request. Approving hard-deletes the
 * participant's rows; rejecting reverts them to {@code accepted}.
 *
 * @param eventId     the event
 * @param title       its title, for the notification text
 * @param approved    {@code true} if the withdrawal was approved (the participant is now off the
 *                    entry list), {@code false} if it was rejected (they remain in the line-up)
 * @param recipientId the participant who requested the withdrawal
 */
public record MapEventWithdrawalDecidedEvent(
        UUID eventId,
        String title,
        boolean approved,
        UUID recipientId
) {}
