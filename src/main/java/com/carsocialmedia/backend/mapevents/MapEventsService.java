package com.carsocialmedia.backend.mapevents;

import com.carsocialmedia.backend.mapevents.dto.CarMeetDetailsDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventAttendeeDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventCategoryDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventPageDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventParticipantDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventPinDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventSummaryDto;
import com.carsocialmedia.backend.mapevents.dto.request.AddOrganizerRequest;
import com.carsocialmedia.backend.mapevents.dto.request.CreateMapEventRequest;
import com.carsocialmedia.backend.mapevents.dto.request.UpdateMapEventRequest;

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
     * @throws com.carsocialmedia.backend.mapevents.exception.InvalidSearchAreaException if any bound is violated
     */
    List<MapEventPinDto> findNearby(double lat, double lng, double radiusKm, String categoryId, int limit);

    /** All selectable event subcategories (reference data), by label. */
    List<MapEventCategoryDto> listCategories();

    // ===================== Event page =====================

    /**
     * One event's full detail page, including the caller's own standing on it.
     *
     * @throws com.carsocialmedia.backend.mapevents.exception.MapEventNotFoundException if no such
     *         event exists <em>or</em> it is unapproved and the caller does not organize it — the
     *         two are deliberately indistinguishable, so pending submissions cannot be probed for.
     */
    MapEventDto getEvent(UUID currentUserId, UUID eventId);

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
     * <p>Anyone may read the {@code accepted} line-up. {@code pending} and {@code rejected} are
     * organizer-only, since they expose entries that were turned down.
     *
     * @param status optional filter; {@code null} = the accepted line-up
     * @throws com.carsocialmedia.backend.mapevents.exception.NotEventOrganizerException if a
     *         non-organizer asks for a non-accepted status
     */
    MapEventPageDto<MapEventParticipantDto> listParticipants(UUID currentUserId, UUID eventId, String status,
                                                             String cursor, int size);

    /**
     * One keyset page of the events the caller created or co-organizes, newest first — including
     * their pending and rejected ones, which is the point of the screen.
     */
    MapEventPageDto<MapEventSummaryDto> getMyEvents(UUID currentUserId, String cursor, int size);

    // ===================== Authoring =====================

    /**
     * Creates an event, submitted for approval. The caller becomes {@code created_by} and is
     * inserted as the {@code creator} organizer.
     *
     * <p>The cover image is attached afterwards via {@link #saveCoverImageKey}: the event row must
     * exist before an upload URL can be namespaced to it.
     *
     * @throws com.carsocialmedia.backend.mapevents.exception.InvalidMapEventException if the
     *         category is unknown or unavailable, the timings are inconsistent, or a car meet is
     *         missing its registration deadline
     */
    MapEventDto createEvent(UUID currentUserId, CreateMapEventRequest request);

    /**
     * Partial update by an organizer. Allowed only while the event is pending or rejected; editing
     * a rejected event clears its rejection reason and resubmits it as pending.
     *
     * @throws com.carsocialmedia.backend.mapevents.exception.NotEventOrganizerException if the caller does not organize it
     * @throws com.carsocialmedia.backend.mapevents.exception.EventNotEditableException if it has been approved, cancelled or finished
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
     * @throws com.carsocialmedia.backend.mapevents.exception.InvalidMapEventException if the request
     *         names neither or both of a user and a business, if the target does not exist, or if
     *         they already organize this event
     */
    MapEventDto addOrganizer(UUID currentUserId, UUID eventId, AddOrganizerRequest request);

    /**
     * Removes a co-organizer by their {@code car_event_organizers} row id. Creator only; the
     * creator's own row can never be removed.
     */
    MapEventDto removeOrganizer(UUID currentUserId, UUID eventId, UUID organizerId);

    // ===================== Attending (spectators) =====================

    /**
     * Sets or changes the caller's RSVP — {@code attending} or {@code interested}. Open to any
     * authenticated user on an approved event, with no organizer approval; idempotent.
     *
     * @throws com.carsocialmedia.backend.mapevents.exception.EventClosedException if the event has
     *         finished or been cancelled
     */
    MapEventDto setAttendance(UUID currentUserId, UUID eventId, String status);

    /** Withdraws the caller's RSVP. Idempotent. */
    MapEventDto removeAttendance(UUID currentUserId, UUID eventId);

    // ===================== Participating (cars) =====================

    /**
     * Enters one of the caller's own cars into the event. Starts {@code pending} when the event
     * requires organizer approval, otherwise {@code accepted}. Idempotent per car.
     *
     * @throws com.carsocialmedia.backend.mapevents.exception.CarNotOwnedException if the car is not the caller's
     * @throws com.carsocialmedia.backend.mapevents.exception.EventClosedException if the registration deadline has passed
     */
    MapEventParticipantDto registerCar(UUID currentUserId, UUID eventId, UUID carId);

    /** Withdraws one of the caller's cars from the event. Idempotent. */
    void withdrawCar(UUID currentUserId, UUID eventId, UUID carId);

    /**
     * An organizer's verdict on an entered car: {@code accepted} or {@code rejected}.
     *
     * @throws com.carsocialmedia.backend.mapevents.exception.NotEventOrganizerException if the caller does not organize the event
     */
    MapEventParticipantDto decideParticipant(UUID currentUserId, UUID eventId, UUID carId, String status);

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
