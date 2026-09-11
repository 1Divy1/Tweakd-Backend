package com.tweakdapp.backend.mapevents;

import com.tweakdapp.backend.mapevents.dto.ParticipantCardDto;
import com.tweakdapp.backend.mapevents.dto.ParticipantCardKey;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import com.tweakdapp.backend.mapevents.dto.CarEventHistoryItemDto;
import com.tweakdapp.backend.mapevents.dto.ContestCategoryDto;
import com.tweakdapp.backend.mapevents.dto.ContestDto;
import com.tweakdapp.backend.mapevents.dto.request.CreateContestRequest;
import com.tweakdapp.backend.mapevents.dto.request.UpdateContestRequest;

import java.util.List;
import java.util.UUID;

/**
 * Contests that run <strong>inside</strong> a car event: an organizer opens a vote per category,
 * owners of accepted cars ask to be judged, attendees vote, and closing the vote pays out.
 *
 * <p>Every write returns the fresh {@link ContestDto}, following the module's "every write returns
 * the thing" rule, so the app never follows a write with a read to learn the new counts.
 *
 * <h2>Who may do what</h2>
 * <ul>
 *   <li><strong>Organizers</strong> (individuals only, as everywhere in this module): create, edit,
 *       open voting, finish it, delete while scheduled, accept / reject entry requests.</li>
 *   <li><strong>Car owners</strong> whose car is an <em>accepted</em> participant of the event: ask
 *       to enter one or more contests; withdraw a pending request any time, an accepted entry only
 *       while the contest is still scheduled.</li>
 *   <li><strong>Attendees</strong> with an {@code attending} RSVP: vote once per open contest and
 *       change it until an organizer finishes it. Never for a car they own.</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * {@code scheduled} → {@code open} → {@code finished}, and <strong>every step is an organizer's
 * tap</strong>. Nothing in this module runs on a timer: a contest opens when an organizer opens it
 * and finishes when they finish it, or when the event itself is marked finished or cancelled (that
 * cascade closes every still-open contest). {@code opens_at} and {@code closes_at} are the planned
 * window shown to attendees — a label, never a trigger. Only an explicit finish finalises — never a
 * read — because finalising awards reputation and badges.
 */
public interface MapEventContestsService {

    /** The available contest categories, in display order. */
    List<ContestCategoryDto> listCategories();

    /**
     * Every contest of an event, each with its ranked entries embedded — one round trip paints the
     * contests tab. Visibility follows the event's: an unapproved event's contests 404 for anyone
     * who is not an organizer.
     */
    List<ContestDto> listContests(UUID currentUserId, UUID eventId);

    ContestDto getContest(UUID currentUserId, UUID eventId, UUID contestId);

    /**
     * Creates a contest inside the event. Organizer only; the event must be approved and not over.
     * At most 20 contests per event.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidContestException on a bad window,
     *         an unknown category, or a custom category with a blank title
     * @throws com.tweakdapp.backend.mapevents.exception.ContestLimitException when the event
     *         already has 20 contests
     */
    ContestDto createContest(UUID currentUserId, UUID eventId, CreateContestRequest request);

    /**
     * Partial update. While {@code scheduled} anything may change; while {@code open} only
     * {@code closes_at} (moving the planned end) and {@code criteria}. Editing the window never
     * opens or closes anything — use {@link #openContest} and {@link #finishContest} for that.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.ContestClosedException once finished
     */
    ContestDto updateContest(UUID currentUserId, UUID eventId, UUID contestId, UpdateContestRequest request);

    /**
     * Opens voting on a scheduled contest, now: attendees may vote and the meet is told. Organizer
     * only, and only on an approved event that is not over. Idempotent — opening an open contest
     * returns it unchanged.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.ContestClosedException once finished, or
     *         when the event is not approved / already over
     */
    ContestDto openContest(UUID currentUserId, UUID eventId, UUID contestId);

    /**
     * Finishes an open contest now: standings freeze, the top three are awarded, everyone at the
     * meet is told. Idempotent — finishing a finished contest returns it unchanged.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.ContestNotOpenException if voting has not
     *         opened yet (open or delete it instead)
     */
    ContestDto finishContest(UUID currentUserId, UUID eventId, UUID contestId);

    /** Deletes a contest that has not opened yet. An open contest is finished, never deleted. */
    void deleteContest(UUID currentUserId, UUID eventId, UUID contestId);

    /**
     * Asks to enter one of the caller's cars into a contest. The car must be an accepted
     * participant of the event and the contest must not be finished. Re-sending an existing
     * pending or accepted entry is a no-op; a rejected or withdrawn one goes back to pending.
     */
    ContestDto requestEntry(UUID currentUserId, UUID eventId, UUID contestId, UUID carId);

    /**
     * Withdraws the caller's car from a contest. A pending request is deleted outright; an accepted
     * entry may leave only while the contest is still scheduled.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.ContestClosedException when trying to pull
     *         an accepted entry out of a contest whose voting has opened
     */
    ContestDto withdrawEntry(UUID currentUserId, UUID eventId, UUID contestId, UUID carId);

    /**
     * An organizer's verdict on an entry request. Rejecting requires a reason. Accepting is capped
     * at 40 entries per contest. Accepting during voting is allowed — the car starts at zero.
     */
    ContestDto decideEntry(UUID currentUserId, UUID eventId, UUID contestId, UUID carId, String status, String reason);

    /**
     * Casts or changes the caller's vote. One vote per contest; voting for the same car again is a
     * no-op.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.NotEligibleToVoteException when the caller
     *         has not RSVP'd {@code attending}, or owns the car
     * @throws com.tweakdapp.backend.mapevents.exception.ContestNotOpenException while the contest
     *         is still scheduled — the planned {@code opens_at} does not open it, an organizer does
     * @throws com.tweakdapp.backend.mapevents.exception.ContestClosedException once it is finished
     */
    ContestDto vote(UUID currentUserId, UUID eventId, UUID contestId, UUID carId);

    /**
     * A car's event history: every event it was an accepted participant of that is running or
     * over, newest first, with any podium placements it earned in that event's contests. Backs the
     * "Attended events" section of the car page. Capped at 50.
     */
    List<CarEventHistoryItemDto> getCarHistory(UUID currentUserId, UUID carId);

    // ── Participant cards ────────────────────────────────────────────────

    /**
     * The caller's participant cards for an event — one per car they had accepted into it. Empty
     * for spectators, for a cancelled event, and until an organizer marks the event finished: only
     * then does a card exist.
     */
    List<ParticipantCardDto> listMyParticipantCards(UUID currentUserId, UUID eventId);

    /**
     * Resolves many cards at once — how a feed page draws the cards its posts share, in a fixed
     * number of queries. A key that is not (or is no longer) a card is simply absent from the
     * result, so its post degrades to a plain one. Not viewer-scoped: a card reads the same for
     * everyone.
     */
    Map<ParticipantCardKey, ParticipantCardDto> findParticipantCards(Collection<ParticipantCardKey> keys);

    /**
     * The card for {@code (eventId, carId)}, provided it exists and {@code ownerId} owns that car's
     * entry — the check behind sharing a card to the feed.
     */
    Optional<ParticipantCardDto> findOwnedParticipantCard(UUID ownerId, UUID eventId, UUID carId);
}
