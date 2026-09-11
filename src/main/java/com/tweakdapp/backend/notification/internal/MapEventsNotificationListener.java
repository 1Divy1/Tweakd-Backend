package com.tweakdapp.backend.notification.internal;

import com.tweakdapp.backend.mapevents.ContestEntryDecidedEvent;
import com.tweakdapp.backend.mapevents.ContestEntryRequestedEvent;
import com.tweakdapp.backend.mapevents.ContestFinishedEvent;
import com.tweakdapp.backend.mapevents.ContestOpenedEvent;
import com.tweakdapp.backend.mapevents.MapEventApprovedEvent;
import com.tweakdapp.backend.mapevents.MapEventCarDecidedEvent;
import com.tweakdapp.backend.mapevents.MapEventCarRegisteredEvent;
import com.tweakdapp.backend.mapevents.MapEventOrganizerAddedEvent;
import com.tweakdapp.backend.mapevents.MapEventRejectedEvent;
import com.tweakdapp.backend.mapevents.MapEventWithdrawalDecidedEvent;
import com.tweakdapp.backend.mapevents.MapEventWithdrawalRequestedEvent;
import com.tweakdapp.backend.mapevents.ParticipantCardsReadyEvent;
import com.tweakdapp.backend.notification.NotificationService;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns {@code mapevents} domain events into in-app notifications. Each handler runs
 * asynchronously, in its own new transaction, after the producing transaction commits, so a
 * rolled-back approval or registration never notifies — the same shape as
 * {@link PostsNotificationListener}.
 *
 * <h2>Which of these are preference-gated</h2>
 * <ul>
 *   <li><strong>Ungated</strong> — {@code map_event_approved}, {@code map_event_rejected},
 *       {@code map_event_car_decided}, {@code map_event_withdrawal_decided}, {@code contest_placed}
 *       and {@code participant_card_ready}. These are decisions
 *       about the recipient's <em>own</em> submission, so they follow {@code feedback_status} and
 *       {@code moderation_warning}: you do not opt out of being told what happened to something you
 *       submitted.</li>
 *   <li><strong>Gated on {@code event_organizer_enabled}</strong> —
 *       {@code map_event_car_registered}, {@code map_event_organizer_added} and
 *       {@code map_event_withdrawal_requested}, the running-an-event notifications aimed at
 *       organizers. {@code organized_events_enabled} is reserved for attendee-facing event logistics
 *       (delays, cancellations) and has no producer yet.</li>
 * </ul>
 */
@Component
class MapEventsNotificationListener {

    private final ProfileService profileService;
    private final NotificationService notificationService;

    MapEventsNotificationListener(ProfileService profileService, NotificationService notificationService) {
        this.profileService = profileService;
        this.notificationService = notificationService;
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(MapEventApprovedEvent event) {
        notificationService.push(
                event.recipientId(),
                "map_event_approved",
                "Your event \"" + event.title() + "\" is now live on the map",
                null,
                payload(event.eventId()));
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(MapEventRejectedEvent event) {
        // The reason goes in the body: it is the actionable part — editing the event resubmits it.
        notificationService.push(
                event.recipientId(),
                "map_event_rejected",
                "Your event \"" + event.title() + "\" was not approved",
                event.reason(),
                payload(event.eventId()));
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(MapEventCarDecidedEvent event) {
        Map<String, Object> payload = payload(event.eventId());
        payload.put("car_id", event.carId().toString());

        notificationService.push(
                event.recipientId(),
                "map_event_car_decided",
                event.accepted()
                        ? "Your car is in the line-up for \"" + event.title() + "\""
                        : "Your car was not accepted for \"" + event.title() + "\"",
                event.accepted() ? null : event.reason(),
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(MapEventCarRegisteredEvent event) {
        List<UUID> recipients = event.recipientIds().stream()
                .filter(id -> profileService.getNotificationPreferencesOrDefault(id).eventOrganizerEnabled())
                .toList();
        if (recipients.isEmpty()) {
            return;
        }

        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = payload(event.eventId());
        payload.put("car_id", event.carId().toString());
        payload.put("actor_id", event.actorId().toString());
        payload.put("actor_username", username);

        notificationService.pushToAll(
                recipients,
                "map_event_car_registered",
                username + " entered a car in \"" + event.title() + "\"",
                null,
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(MapEventOrganizerAddedEvent event) {
        if (!profileService.getNotificationPreferencesOrDefault(event.recipientId()).eventOrganizerEnabled()) {
            return;
        }

        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = payload(event.eventId());
        payload.put("actor_id", event.actorId().toString());
        payload.put("actor_username", username);

        notificationService.push(
                event.recipientId(),
                "map_event_organizer_added",
                username + " added you as an organizer of \"" + event.title() + "\"",
                null,
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(MapEventWithdrawalRequestedEvent event) {
        List<UUID> recipients = event.recipientIds().stream()
                .filter(id -> profileService.getNotificationPreferencesOrDefault(id).eventOrganizerEnabled())
                .toList();
        if (recipients.isEmpty()) {
            return;
        }

        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = payload(event.eventId());
        payload.put("actor_id", event.actorId().toString());
        payload.put("actor_username", username);
        payload.put("note", event.note());

        notificationService.pushToAll(
                recipients,
                "map_event_withdrawal_requested",
                username + " asked to withdraw from \"" + event.title() + "\"",
                event.note(),
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(MapEventWithdrawalDecidedEvent event) {
        notificationService.push(
                event.recipientId(),
                "map_event_withdrawal_decided",
                event.approved()
                        ? "Your withdrawal from \"" + event.title() + "\" was approved"
                        : "Your withdrawal from \"" + event.title() + "\" was declined",
                null,
                payload(event.eventId()));
    }

    // ---- contests -----------------------------------------------------------

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(ContestEntryRequestedEvent event) {
        List<UUID> recipients = event.recipientIds().stream()
                .filter(id -> profileService.getNotificationPreferencesOrDefault(id).eventOrganizerEnabled())
                .toList();
        if (recipients.isEmpty()) {
            return;
        }

        String username = resolveUsername(event.actorId());
        Map<String, Object> payload = contestPayload(event.eventId(), event.contestId());
        payload.put("car_id", event.carId().toString());
        payload.put("actor_id", event.actorId().toString());
        payload.put("actor_username", username);

        notificationService.pushToAll(
                recipients,
                "contest_entry_requested",
                username + " wants to enter \"" + event.contestTitle() + "\"",
                event.eventTitle(),
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(ContestEntryDecidedEvent event) {
        Map<String, Object> payload = contestPayload(event.eventId(), event.contestId());
        payload.put("car_id", event.carId().toString());
        payload.put("accepted", event.accepted());

        // A decision on your own request is ungated, like every other "what happened to my
        // submission" notification.
        notificationService.push(
                event.recipientId(),
                "contest_entry_decided",
                event.accepted()
                        ? "Your car is on the ballot for \"" + event.contestTitle() + "\""
                        : "Your car was not accepted into \"" + event.contestTitle() + "\"",
                event.accepted() ? null : event.reason(),
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(ContestOpenedEvent event) {
        List<UUID> recipients = event.recipientIds().stream()
                .filter(id -> profileService.getNotificationPreferencesOrDefault(id).organizedEventsEnabled())
                .toList();
        if (recipients.isEmpty()) {
            return;
        }

        notificationService.pushToAll(
                recipients,
                "contest_opened",
                "Voting is open: " + event.contestTitle(),
                event.eventTitle(),
                contestPayload(event.eventId(), event.contestId()));
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(ContestFinishedEvent event) {
        // The podium first: personal, ungated — you are told when your car places.
        for (ContestFinishedEvent.Placement place : event.podium()) {
            Map<String, Object> payload = contestPayload(event.eventId(), event.contestId());
            payload.put("car_id", place.carId().toString());
            payload.put("rank", place.rank());
            notificationService.push(
                    place.ownerId(),
                    "contest_placed",
                    "Your " + place.carName() + " took " + ordinal(place.rank()) + " in \"" + event.contestTitle() + "\"",
                    event.eventTitle(),
                    payload);
        }

        List<UUID> audience = event.audienceIds().stream()
                .filter(id -> profileService.getNotificationPreferencesOrDefault(id).organizedEventsEnabled())
                .toList();
        if (audience.isEmpty()) {
            return;
        }
        Map<String, Object> payload = contestPayload(event.eventId(), event.contestId());
        payload.put("winner_car_id", event.winnerCarId().toString());
        notificationService.pushToAll(
                audience,
                "contest_results",
                "Results are in: " + event.contestTitle(),
                event.eventTitle(),
                payload);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(ParticipantCardsReadyEvent event) {
        // Ungated, like contest_placed: it is about the recipient's own car and fires once per
        // event, so there is nothing to opt out of that would not also hide the card itself.
        notificationService.pushToAll(
                event.ownerIds(),
                "participant_card_ready",
                "Your card from \"" + event.eventTitle() + "\" is ready",
                "Share it to your feed",
                payload(event.eventId()));
    }

    private Map<String, Object> contestPayload(UUID eventId, UUID contestId) {
        Map<String, Object> payload = payload(eventId);
        payload.put("contest_id", contestId.toString());
        return payload;
    }

    private static String ordinal(int rank) {
        return switch (rank) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> rank + "th";
        };
    }

    /**
     * Payload keys are literal snake_case strings: the global SNAKE_CASE wire strategy renames POJO
     * fields, not JSON-map keys, so they have to be spelled out here.
     */
    private Map<String, Object> payload(UUID eventId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", eventId.toString());
        return payload;
    }

    private String resolveUsername(UUID actorId) {
        return profileService.findByIds(List.of(actorId)).stream()
                .findFirst()
                .map(ProfileSearchResultDto::username)
                .orElse("Someone");
    }
}
