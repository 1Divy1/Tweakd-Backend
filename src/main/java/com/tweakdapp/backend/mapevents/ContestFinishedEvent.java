package com.tweakdapp.backend.mapevents;

import java.util.List;
import java.util.UUID;

/**
 * Published when a contest is finalised with at least one vote cast: the standings are frozen and
 * the podium has been awarded.
 *
 * <p>Not published for a contest finalised without awards (an event that was cancelled) or one
 * that nobody voted in — there is no result to announce.
 *
 * @param eventId      the event the contest belongs to
 * @param eventTitle   its title
 * @param contestId    the contest
 * @param contestTitle its title, for the notification text
 * @param winnerCarId  the car that took first place
 * @param podium       the top three (or fewer), rank ascending
 * @param audienceIds  everyone at the meet who should hear the result — {@code attending} RSVPs
 *                     plus the owners of accepted entries — <em>excluding</em> the podium owners,
 *                     who get their own, personal notification
 */
public record ContestFinishedEvent(
        UUID eventId,
        String eventTitle,
        UUID contestId,
        String contestTitle,
        UUID winnerCarId,
        List<Placement> podium,
        List<UUID> audienceIds
) {

    /**
     * One podium place.
     *
     * @param ownerId the car's owner — the person who is told and awarded
     * @param carId   the placed car
     * @param rank    1, 2 or 3
     * @param carName "Brand Model", for the notification text
     */
    public record Placement(UUID ownerId, UUID carId, int rank, String carName) {}
}
