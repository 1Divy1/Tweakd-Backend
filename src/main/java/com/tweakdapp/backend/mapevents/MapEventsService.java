package com.tweakdapp.backend.mapevents;

import com.tweakdapp.backend.mapevents.dto.CarMeetDetailsDto;
import com.tweakdapp.backend.mapevents.dto.GeocodeCandidateDto;
import com.tweakdapp.backend.mapevents.dto.MapEventAttendeeDto;
import com.tweakdapp.backend.mapevents.dto.MapEventCategoryDto;
import com.tweakdapp.backend.mapevents.dto.MapEventDto;
import com.tweakdapp.backend.mapevents.dto.MapEventPageDto;
import com.tweakdapp.backend.mapevents.dto.MapEventParticipantDto;
import com.tweakdapp.backend.mapevents.dto.MapEventPinDto;
import com.tweakdapp.backend.mapevents.dto.MapEventSummaryDto;
import com.tweakdapp.backend.mapevents.dto.MapEventWithdrawalRequestDto;
import com.tweakdapp.backend.mapevents.dto.OrganizerCandidateDto;
import com.tweakdapp.backend.mapevents.dto.PublicMapEventDto;
import com.tweakdapp.backend.mapevents.dto.request.AddOrganizerRequest;
import com.tweakdapp.backend.mapevents.dto.request.CreateMapEventRequest;
import com.tweakdapp.backend.mapevents.dto.request.GeocodeQuery;
import com.tweakdapp.backend.mapevents.dto.request.UpdateMapEventRequest;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Car events on the app's map: authoring them, taking part in them, and the admin approval gate
 * they pass through before they become visible.
 *
 * <p>Two independent states govern an event. <strong>Approval</strong> is the admin gate — nothing
 * reaches the map until an admin accepts it, and acceptance locks the event against organizer
 * edits. <strong>Status</strong> is the event's own life (upcoming / live / previous / hidden /
 * canceled); it is stored, never derived, and nothing transitions it automatically, so read paths
 * additionally take the clock into account.
 */
public interface MapEventsService {

    // ===================== Map =====================

    /**
     * Approved, still-running events within {@code radiusKm} of a centre point, nearest first — the
     * query behind the map. Each pin already carries what the floating widget needs, so tapping a
     * marker costs no extra request.
     *
     * <p>The caller supplies the centre, exactly as for businesses: the user's realtime location
     * when they granted location permission, otherwise their home city's coordinates.
     *
     * @param lat        centre latitude, -90..90
     * @param lng        centre longitude, -180..180
     * @param radiusKm   search radius in kilometres, 0 &lt; radiusKm &le; 500
     * @param categoryId optional subcategory filter (e.g. {@code car_meet}); {@code null} = all
     * @param limit      maximum pins to return, 1..500
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidSearchAreaException if any bound is violated
     */
    List<MapEventPinDto> findNearby(double lat, double lng, double radiusKm, String categoryId, int limit);

    /**
     * The map's search box: approved events whose title, category label or venue name contains
     * {@code query} (case-insensitive), <strong>nearest to the centre first</strong>, one keyset
     * page at a time. Unlike {@link #findNearby} there is no radius — search reaches the whole map,
     * and it can reach finished events too.
     *
     * <p>Each pin's {@code status} is the <em>clock-derived</em> phase ({@code upcoming},
     * {@code live} or {@code previous}), not the stored column, which nothing updates
     * automatically. Canceled, hidden and unapproved events never match.
     *
     * @param query    search text; fewer than 2 non-blank characters returns an empty page
     * @param lat      centre latitude, -90..90 — the map's current centre
     * @param lng      centre longitude, -180..180
     * @param statuses phases to include, any of {@code upcoming}, {@code live}, {@code previous};
     *                 {@code null} or empty means {@code upcoming} + {@code live}
     * @param cursor   opaque token from the previous page (same centre and statuses), or
     *                 {@code null} for the first
     * @param size     page size, clamped to 1..50 (0 or less means the default, 20)
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidSearchAreaException if the centre is
     *         out of range
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidMapEventException if a status is not
     *         one of the three phases
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidCursorException if the cursor cannot
     *         be parsed
     */
    MapEventPageDto<MapEventPinDto> search(String query, double lat, double lng, Collection<String> statuses,
                                           String cursor, int size);

    /** All selectable event subcategories (reference data), by label. */
    List<MapEventCategoryDto> listCategories();

    /**
     * Forward-geocodes a structured address into map coordinates — backs the create-event
     * location picker's dedicated address fields (street, number, city, ...). Proxied through
     * Mapbox's Geocoding v6 API, in its "structured input" mode, so the access token never
     * reaches the client, request volume stays under this backend's control, and match accuracy
     * is better than a single free-text string.
     *
     * <p>Always queried with {@code permanent=false}: a candidate the caller taps only recentres
     * the map, it is not itself persisted — the event's actual location is a pin the user drops
     * afterwards, their own input rather than Mapbox's.
     *
     * @param query        the structured address fields; blank (every field null or blank)
     *                     returns an empty list without a Mapbox request, same idea as a blank
     *                     {@code q} used to be for {@link #searchOrganizerCandidates}
     * @param proximityLat optional bias latitude, typically the picker's current camera centre,
     *                     -90..90 — applied only when {@code proximityLng} is also a valid
     *                     coordinate; an invalid or lone value is dropped rather than failing the
     *                     search, since it is only a relevance hint
     * @param proximityLng optional bias longitude, -180..180; see {@code proximityLat}
     * @return candidates ordered by relevance, best match first, up to 5 of them. The client
     *         shows all of them as a tappable list rather than flying to the first hit.
     */
    List<GeocodeCandidateDto> geocode(GeocodeQuery query, Double proximityLat, Double proximityLng);

    // ===================== Event page =====================

    /**
     * One event's full detail page, including the caller's own standing on it.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.MapEventNotFoundException if no such
     *         event exists <em>or</em> it is unapproved and the caller does not organize it — the
     *         two are deliberately indistinguishable, so pending submissions cannot be probed for.
     */
    MapEventDto getEvent(UUID currentUserId, UUID eventId);

    /**
     * An event as an anonymous visitor sees it through a shared link — the public page at
     * {@code web.tweakdapp.com/e/{eventId}}. No caller: it runs for someone without an account.
     *
     * <p>Approved and still on: served, including once it has finished. Cancelled: gone.
     * Everything else — pending, rejected, hidden, deleted, never existed — is one 404, so a
     * submission cannot be found by probing ids, exactly as in {@link #getEvent}.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.MapEventGoneException if it was cancelled
     * @throws com.tweakdapp.backend.mapevents.exception.MapEventNotFoundException otherwise
     */
    PublicMapEventDto getPublicEvent(UUID eventId);

    /**
     * One keyset page of an event's attendees (spectators), most recent RSVP first.
     *
     * @param status optional filter: {@code attending} or {@code interested}; {@code null} = both
     */
    MapEventPageDto<MapEventAttendeeDto> listAttendees(UUID currentUserId, UUID eventId, String status,
                                                       String cursor, int size);

    /**
     * One keyset page of the cars entered into an event, newest registration first.
     *
     * <p>Anyone may read the public line-up: {@code accepted} cars, plus {@code withdrawn} ones
     * (a pending withdrawal does not remove a participant from the list, only flags it via their
     * {@code status}). {@code pending} and {@code rejected} are organizer-only, since they expose
     * entries that were turned down.
     *
     * @param status optional filter: {@code pending}, {@code accepted}, {@code rejected} or
     *               {@code withdrawn}; {@code null} = the public line-up ({@code accepted} +
     *               {@code withdrawn})
     * @throws com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException if a
     *         non-organizer asks for {@code pending} or {@code rejected}
     */
    MapEventPageDto<MapEventParticipantDto> listParticipants(UUID currentUserId, UUID eventId, String status,
                                                             String cursor, int size);

    /**
     * Every one of the caller's own cars entered into an event, whatever their status — the one
     * place a participant can see a {@code pending} or {@code rejected} entry of their own, since
     * {@link #listParticipants} hides those from anyone but an organizer.
     */
    List<MapEventParticipantDto> listMyParticipants(UUID currentUserId, UUID eventId);

    /**
     * One keyset page of the events the caller created or co-organizes, newest first — including
     * their pending and rejected ones, which is the point of the screen.
     */
    MapEventPageDto<MapEventSummaryDto> getMyEvents(UUID currentUserId, String cursor, int size);

    // ===================== Authoring =====================

    /**
     * Creates an event, submitted for approval. The caller becomes {@code created_by} and is
     * inserted as the {@code creator} organizer. If {@code request.rules()} is non-empty, the rules
     * are saved in the same transaction as the event — no follow-up call needed.
     *
     * <p>The cover image is the one exception, attached afterwards via {@link #saveCoverImageKey}:
     * unlike rules, it depends on an external upload (a presigned R2 URL namespaced to the event's
     * id, then the client PUTting the file), so the event row must exist first.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidMapEventException if the
     *         category is unknown or unavailable, the timings are inconsistent, or a car meet is
     *         missing its registration deadline
     */
    MapEventDto createEvent(UUID currentUserId, CreateMapEventRequest request);

    /**
     * Partial update by an organizer. Allowed only while the event is pending or rejected; editing
     * a rejected event clears its rejection reason and resubmits it as pending.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException if the caller does not organize it
     * @throws com.tweakdapp.backend.mapevents.exception.EventNotEditableException if it has been approved, cancelled or finished
     */
    MapEventDto updateEvent(UUID currentUserId, UUID eventId, UpdateMapEventRequest request);

    /**
     * Attaches an uploaded cover image by its R2 key, replacing any previous one (the old object is
     * deleted from R2 after the transaction commits). Organizer only, and only while editable.
     *
     * @param key the key returned by {@code GET /api/storage/events/{eventId}/cover}; it must sit
     *            under this event's own prefix, so one event cannot claim another's upload
     */
    MapEventDto saveCoverImageKey(UUID currentUserId, UUID eventId, String key);

    /**
     * Replaces an event's rules list wholesale, in the given order. {@code sort_order} is assigned
     * server-side from list position; an empty list clears all rules. Organizer only, and only while
     * editable — approval locks the rules exactly as it locks the rest of the event.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException if the caller does not organize it
     * @throws com.tweakdapp.backend.mapevents.exception.EventNotEditableException if it has been approved, cancelled or finished
     */
    MapEventDto replaceRules(UUID currentUserId, UUID eventId, List<String> rules);

    /**
     * Calls the event off ({@code status = canceled}). The page survives so attendees can see what
     * happened; the pin leaves the map. Organizer only, idempotent.
     */
    MapEventDto cancelEvent(UUID currentUserId, UUID eventId);

    /**
     * Marks a running event finished ({@code status = previous}) — the manual transition that
     * stands in for the automatic sweep the owner deferred. Closes RSVP. Organizer only, idempotent.
     */
    MapEventDto markFinished(UUID currentUserId, UUID eventId);

    /**
     * Permanently deletes an event: child rows cascade in the database and the cover image is
     * removed from R2 after the transaction commits.
     *
     * <p><strong>Creator only</strong> — a co-organizer cannot delete somebody else's event.
     * Admins delete through {@link #deleteEventAsAdmin}.
     */
    void deleteEvent(UUID currentUserId, UUID eventId);

    // ===================== Organizers =====================

    /**
     * Credits a co-organizer — an app user or a business account. Creator only.
     *
     * <p>Business accounts have no login yet, so a business organizer is displayed on the event
     * page but holds no permissions.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidMapEventException if the request
     *         names neither or both of a user and a business, if the target does not exist, or if
     *         they already organize this event
     */
    MapEventDto addOrganizer(UUID currentUserId, UUID eventId, AddOrganizerRequest request);

    /**
     * Removes a co-organizer by their {@code car_event_organizers} row id. Creator only; the
     * creator's own row can never be removed.
     */
    MapEventDto removeOrganizer(UUID currentUserId, UUID eventId, UUID organizerId);

    /**
     * Searches app users and businesses by name prefix, merged into one list — candidates for
     * {@link #addOrganizer}. Mirrors {@code ProfileService.searchByUsername} and
     * {@code BusinessService.searchByName}, tagging each hit with which one it came from.
     *
     * @param query search text; blank or {@code null} returns an empty list
     */
    List<OrganizerCandidateDto> searchOrganizerCandidates(UUID currentUserId, String query);

    // ===================== Attending (spectators) =====================

    /**
     * Sets or changes the caller's RSVP — {@code attending} or {@code interested}. Open to any
     * authenticated user on an approved event, with no organizer approval; idempotent.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.EventClosedException if the event has
     *         finished or been cancelled
     */
    MapEventDto setAttendance(UUID currentUserId, UUID eventId, String status);

    /** Withdraws the caller's RSVP. Idempotent. */
    MapEventDto removeAttendance(UUID currentUserId, UUID eventId);

    // ===================== Participating (cars) =====================

    /**
     * Enters one of the caller's own cars into the event. Starts {@code pending} when the event
     * requires organizer approval, otherwise {@code accepted}. Idempotent per car. Returns the full
     * event page, not just the new entry, since {@code attending_cars_count} and the caller's
     * {@code viewer.myRegisteredCarIds} both change and are trigger-owned — one response saves the
     * client an immediate follow-up {@code GET}.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.CarNotOwnedException if the car is not the caller's
     * @throws com.tweakdapp.backend.mapevents.exception.EventClosedException if the registration deadline has passed
     */
    MapEventDto registerCar(UUID currentUserId, UUID eventId, UUID carId);

    /**
     * Withdraws one of the caller's cars from the event outright. Idempotent, and only allowed
     * while the registration is still {@code pending} — nothing has been confirmed yet, so there is
     * nothing to ask an organizer's leave to undo. An {@code accepted} car must go through
     * {@link #requestWithdrawal} instead. Returns the full event page (see {@link #registerCar}).
     *
     * @throws com.tweakdapp.backend.mapevents.exception.CarNotOwnedException if the car is not the caller's
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidMapEventException if the registration is already {@code accepted}
     */
    MapEventDto withdrawCar(UUID currentUserId, UUID eventId, UUID carId);

    /**
     * An organizer's verdict on an entered car: {@code accepted} or {@code rejected}. Returns the
     * full event page (see {@link #registerCar}) as seen by the deciding organizer.
     *
     * @param reason required when rejecting — the owner sees it on their own entry; ignored (and
     *               cleared) when accepting
     * @throws com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException if the caller does not organize the event
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidMapEventException if rejecting without a reason
     */
    MapEventDto decideParticipant(UUID currentUserId, UUID eventId, UUID carId, String status, String reason);

    // ===================== Withdrawal requests =====================

    /**
     * Requests withdrawal from an event: every one of the caller's {@code accepted} rows for it is
     * flagged {@code withdrawn} and an organizer is notified. The rows are <strong>not</strong>
     * removed — the caller stays on the entry list, with the pending withdrawal visible via their
     * {@code status}, until an organizer decides.
     *
     * Returns the full event page (see {@link #registerCar}).
     *
     * @param note optional context for the organizer, or {@code null}
     * @throws com.tweakdapp.backend.mapevents.exception.EventClosedException if the event has finished
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidMapEventException if the caller has no accepted registration on this event
     */
    MapEventDto requestWithdrawal(UUID currentUserId, UUID eventId, String note);

    /**
     * The event's pending withdrawal requests, one entry per requesting owner. Organizer only.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException if the caller does not organize the event
     */
    List<MapEventWithdrawalRequestDto> listWithdrawalRequests(UUID currentUserId, UUID eventId);

    /**
     * Approves a participant's withdrawal request: every one of their rows for this event is
     * hard-deleted, actually taking them off the entry list. Returns the full event page (see
     * {@link #registerCar}) as seen by the deciding organizer.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException if the caller does not organize the event
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidMapEventException if that owner has no pending withdrawal request
     */
    MapEventDto approveWithdrawal(UUID currentUserId, UUID eventId, UUID ownerId);

    /**
     * Rejects a participant's withdrawal request: every one of their rows for this event reverts to
     * {@code accepted}, staying in the line-up. {@code withdraw_note} is left as a historical record
     * of the attempt rather than cleared. Returns the full event page (see {@link #registerCar}) as
     * seen by the deciding organizer.
     *
     * @throws com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException if the caller does not organize the event
     * @throws com.tweakdapp.backend.mapevents.exception.InvalidMapEventException if that owner has no pending withdrawal request
     */
    MapEventDto rejectWithdrawal(UUID currentUserId, UUID eventId, UUID ownerId);

    // ===================== Admin (called by the admin module) =====================

    /**
     * One keyset page of events in a given approval state, <strong>oldest first</strong> so the
     * longest-waiting submission is reviewed first. The dashboard's review queue.
     *
     * <p>Capability checks live in the {@code admin} module; this method assumes an authorized
     * caller.
     *
     * @param approvalStatus {@code pending}, {@code accepted} or {@code rejected}
     */
    MapEventPageDto<MapEventSummaryDto> listEventsForReview(String approvalStatus, String cursor, int size);

    /**
     * One event's full detail for a reviewer, bypassing the "unapproved events are visible only to
     * their organizers" rule that {@link #getEvent} enforces. Capability checks live in the
     * {@code admin} module; this method assumes an authorized caller.
     */
    MapEventDto getEventAsAdmin(UUID eventId);

    /** How many events are waiting for review — the dashboard badge. */
    long countPendingReview();

    /** Approves an event, putting it on the map. Clears any previous rejection reason. */
    MapEventDto approveEvent(UUID eventId);

    /**
     * Rejects an event with a reason the creator will see and can act on. Editing the event
     * afterwards resubmits it.
     */
    MapEventDto rejectEvent(UUID eventId, String reason);

    /** Deletes any event, whoever created it — the admin counterpart of {@link #deleteEvent}. */
    void deleteEventAsAdmin(UUID eventId);

    /** Car-meet detail for one event, or {@code null} if it is not a car meet. */
    CarMeetDetailsDto getCarMeetDetails(UUID eventId);
}
