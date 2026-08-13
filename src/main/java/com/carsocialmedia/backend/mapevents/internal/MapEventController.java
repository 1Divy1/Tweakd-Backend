package com.carsocialmedia.backend.mapevents.internal;

import com.carsocialmedia.backend.mapevents.MapEventsService;
import com.carsocialmedia.backend.mapevents.dto.MapEventAttendeeDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventCategoryDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventPageDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventParticipantDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventPinDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventSummaryDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventWithdrawalRequestDto;
import com.carsocialmedia.backend.mapevents.dto.OrganizerCandidateDto;
import com.carsocialmedia.backend.mapevents.dto.request.AddOrganizerRequest;
import com.carsocialmedia.backend.mapevents.dto.request.AttendanceRequest;
import com.carsocialmedia.backend.mapevents.dto.request.CoverImageKeyRequest;
import com.carsocialmedia.backend.mapevents.dto.request.CreateMapEventRequest;
import com.carsocialmedia.backend.mapevents.dto.request.ParticipantDecisionRequest;
import com.carsocialmedia.backend.mapevents.dto.request.RegisterCarRequest;
import com.carsocialmedia.backend.mapevents.dto.request.ReplaceMapEventRulesRequest;
import com.carsocialmedia.backend.mapevents.dto.request.UpdateMapEventRequest;
import com.carsocialmedia.backend.mapevents.dto.request.WithdrawParticipationRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/map-events")
class MapEventController {

    private final MapEventsService mapEventsService;

    MapEventController(MapEventsService mapEventsService) {
        this.mapEventsService = mapEventsService;
    }

    // ----- Map -----

    /**
     * Events near a point, nearest first — the map screen's query. Each pin already carries what
     * the floating widget shows, so tapping a marker needs no further request; only "view full
     * event" calls {@link #getEvent}.
     *
     * <p>{@code lat}/{@code lng} are the map's centre, chosen by the client: the user's realtime
     * location when permission was granted, otherwise their home city's coordinates.
     */
    @GetMapping("/nearby")
    public List<MapEventPinDto> findNearby(@RequestParam double lat,
                                           @RequestParam double lng,
                                           @RequestParam(name = "radius_km", defaultValue = "25") double radiusKm,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(defaultValue = "200") int limit) {
        return mapEventsService.findNearby(lat, lng, radiusKm, category, limit);
    }

    /** Event subcategories — powers the create screen and the map's category filter. */
    @GetMapping("/categories")
    public List<MapEventCategoryDto> listCategories() {
        return mapEventsService.listCategories();
    }

    /** The caller's own events, including the ones still pending or rejected. */
    @GetMapping("/mine")
    public MapEventPageDto<MapEventSummaryDto> getMyEvents(@AuthenticationPrincipal Jwt jwt,
                                                           @RequestParam(required = false) String cursor,
                                                           @RequestParam(defaultValue = "20") int size) {
        return mapEventsService.getMyEvents(userId(jwt), cursor, size);
    }

    // ----- Event page -----

    @GetMapping("/{eventId}")
    public MapEventDto getEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return mapEventsService.getEvent(userId(jwt), eventId);
    }

    /** An event's attendees (spectators). {@code status} optionally narrows to one RSVP kind. */
    @GetMapping("/{eventId}/attendees")
    public MapEventPageDto<MapEventAttendeeDto> listAttendees(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable UUID eventId,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String cursor,
                                                               @RequestParam(defaultValue = "20") int size) {
        return mapEventsService.listAttendees(userId(jwt), eventId, status, cursor, size);
    }

    /**
     * The cars entered into an event. Defaults to the accepted line-up; {@code pending} and
     * {@code rejected} are organizer-only.
     */
    @GetMapping("/{eventId}/cars")
    public MapEventPageDto<MapEventParticipantDto> listParticipants(@AuthenticationPrincipal Jwt jwt,
                                                                     @PathVariable UUID eventId,
                                                                     @RequestParam(required = false) String status,
                                                                     @RequestParam(required = false) String cursor,
                                                                     @RequestParam(defaultValue = "20") int size) {
        return mapEventsService.listParticipants(userId(jwt), eventId, status, cursor, size);
    }

    /** The caller's own entered cars for this event, whatever their status — including pending/rejected. */
    @GetMapping("/{eventId}/cars/mine")
    public List<MapEventParticipantDto> listMyParticipants(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable UUID eventId) {
        return mapEventsService.listMyParticipants(userId(jwt), eventId);
    }

    // ----- Authoring -----

    /**
     * Creates an event and submits it for approval. The cover image follows separately: request an
     * upload URL from {@code GET /api/storage/events/{eventId}/cover}, PUT the file to R2, then
     * send the key to {@link #saveCover}.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MapEventDto createEvent(@AuthenticationPrincipal Jwt jwt,
                                   @Valid @RequestBody CreateMapEventRequest request) {
        return mapEventsService.createEvent(userId(jwt), request);
    }

    /** Partial update. Allowed only while the event is pending or rejected. */
    @PatchMapping("/{eventId}")
    public MapEventDto updateEvent(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID eventId,
                                   @Valid @RequestBody UpdateMapEventRequest request) {
        return mapEventsService.updateEvent(userId(jwt), eventId, request);
    }

    /** Attaches the uploaded cover image by its R2 key. */
    @PatchMapping("/{eventId}/cover")
    public MapEventDto saveCover(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID eventId,
                                 @Valid @RequestBody CoverImageKeyRequest request) {
        return mapEventsService.saveCoverImageKey(userId(jwt), eventId, request.key());
    }

    /** Replaces the event's rules list wholesale, in order. Allowed only while pending or rejected. */
    @PutMapping("/{eventId}/rules")
    public MapEventDto replaceRules(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID eventId,
                                    @Valid @RequestBody ReplaceMapEventRulesRequest request) {
        return mapEventsService.replaceRules(userId(jwt), eventId, request.rules());
    }

    /** Calls the event off. The page survives; the pin leaves the map. */
    @PostMapping("/{eventId}/cancel")
    public MapEventDto cancelEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return mapEventsService.cancelEvent(userId(jwt), eventId);
    }

    /** Marks a running event finished, closing RSVP. */
    @PostMapping("/{eventId}/finish")
    public MapEventDto markFinished(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return mapEventsService.markFinished(userId(jwt), eventId);
    }

    /** Deletes the event outright. Creator only. */
    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        mapEventsService.deleteEvent(userId(jwt), eventId);
    }

    // ----- Organizers -----

    /**
     * Searches app users and businesses by name — candidates for {@link #addOrganizer}. Individual
     * and business hits come back merged in one list, tagged by {@code type}.
     */
    @GetMapping("/organizers/search")
    public List<OrganizerCandidateDto> searchOrganizerCandidates(@RequestParam("q") String query) {
        return mapEventsService.searchOrganizerCandidates(query);
    }

    /** Credits a co-organizer — an app user or a business account. Creator only. */
    @PostMapping("/{eventId}/organizers")
    public MapEventDto addOrganizer(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID eventId,
                                    @Valid @RequestBody AddOrganizerRequest request) {
        return mapEventsService.addOrganizer(userId(jwt), eventId, request);
    }

    @DeleteMapping("/{eventId}/organizers/{organizerId}")
    public MapEventDto removeOrganizer(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable UUID eventId,
                                       @PathVariable UUID organizerId) {
        return mapEventsService.removeOrganizer(userId(jwt), eventId, organizerId);
    }

    // ----- Attending -----

    /** Sets or changes the caller's RSVP ({@code attending} / {@code interested}). */
    @PutMapping("/{eventId}/attendance")
    public MapEventDto setAttendance(@AuthenticationPrincipal Jwt jwt,
                                     @PathVariable UUID eventId,
                                     @Valid @RequestBody AttendanceRequest request) {
        return mapEventsService.setAttendance(userId(jwt), eventId, request.status());
    }

    @DeleteMapping("/{eventId}/attendance")
    public MapEventDto removeAttendance(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return mapEventsService.removeAttendance(userId(jwt), eventId);
    }

    // ----- Participating cars -----

    /**
     * Enters one of the caller's own cars into the event. Returns the full event page — not just
     * the new entry — since {@code attending_cars_count} and the caller's own registered-car list
     * both change as a result.
     */
    @PostMapping("/{eventId}/cars")
    @ResponseStatus(HttpStatus.CREATED)
    public MapEventDto registerCar(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID eventId,
                                   @Valid @RequestBody RegisterCarRequest request) {
        return mapEventsService.registerCar(userId(jwt), eventId, request.carId());
    }

    /**
     * Withdraws a still-pending registration outright. An accepted one must use
     * {@link #requestWithdrawal}. Returns the full event page (see {@link #registerCar}).
     */
    @DeleteMapping("/{eventId}/cars/{carId}")
    public MapEventDto withdrawCar(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID eventId,
                                   @PathVariable UUID carId) {
        return mapEventsService.withdrawCar(userId(jwt), eventId, carId);
    }

    /** An organizer's verdict on an entered car. Returns the full event page (see {@link #registerCar}). */
    @PatchMapping("/{eventId}/cars/{carId}")
    public MapEventDto decideParticipant(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID eventId,
                                         @PathVariable UUID carId,
                                         @Valid @RequestBody ParticipantDecisionRequest request) {
        return mapEventsService.decideParticipant(userId(jwt), eventId, carId, request.status(), request.reason());
    }

    // ----- Withdrawal requests -----

    /**
     * Requests withdrawal from the event: every one of the caller's accepted cars is flagged, not
     * removed, pending an organizer's decision. Returns the full event page (see
     * {@link #registerCar}).
     */
    @PostMapping("/{eventId}/withdraw")
    public MapEventDto requestWithdrawal(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID eventId,
                                         @Valid @RequestBody WithdrawParticipationRequest request) {
        return mapEventsService.requestWithdrawal(userId(jwt), eventId, request.note());
    }

    /** The event's pending withdrawal requests, one entry per requesting owner. Organizer only. */
    @GetMapping("/{eventId}/withdrawals")
    public List<MapEventWithdrawalRequestDto> listWithdrawalRequests(@AuthenticationPrincipal Jwt jwt,
                                                                      @PathVariable UUID eventId) {
        return mapEventsService.listWithdrawalRequests(userId(jwt), eventId);
    }

    /**
     * Approves a withdrawal request: the owner's cars are removed from the event outright. Returns
     * the full event page (see {@link #registerCar}).
     */
    @PostMapping("/{eventId}/withdrawals/{ownerId}/approve")
    public MapEventDto approveWithdrawal(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID eventId,
                                         @PathVariable UUID ownerId) {
        return mapEventsService.approveWithdrawal(userId(jwt), eventId, ownerId);
    }

    /**
     * Rejects a withdrawal request: the owner's cars stay in the line-up as accepted. Returns the
     * full event page (see {@link #registerCar}).
     */
    @PostMapping("/{eventId}/withdrawals/{ownerId}/reject")
    public MapEventDto rejectWithdrawal(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID eventId,
                                        @PathVariable UUID ownerId) {
        return mapEventsService.rejectWithdrawal(userId(jwt), eventId, ownerId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
