package com.tweakdapp.backend.mapevents.dto;

import com.tweakdapp.backend.garage.dto.CarSummaryDto;

import java.util.List;
import java.util.UUID;

/**
 * A participant card: the shareable summary of one car's day at one event. Every participant — a
 * car with an {@code accepted} row in {@code car_event_participants} — gets one per car; spectators
 * (RSVPs) get none.
 *
 * <p>A card is not stored. It is the pair {@code (eventId, car.id())}, derived on read from data
 * that is already frozen once contests finish, so nobody — owner included — can create or edit
 * one. Every field is public on the event page to anyone who can see the event; nothing here is
 * viewer-scoped, so the same card reads identically for everyone.
 *
 * @param eventId             the event
 * @param eventTitle          its display title
 * @param eventAttendeesCount RSVP head-count, for the card's "N people were at the event" line
 * @param car                 the participating car
 * @param bestRank            the car's best podium place across the event's contests, or
 *                            {@code null} when it placed nowhere (the card then has no pill)
 * @param contests            every finished contest the car was entered in, podium places first;
 *                            empty when it entered none (the card then has no contests row)
 */
public record ParticipantCardDto(
        UUID eventId,
        String eventTitle,
        int eventAttendeesCount,
        CarSummaryDto car,
        Integer bestRank,
        List<ParticipantCardContestDto> contests
) {}
