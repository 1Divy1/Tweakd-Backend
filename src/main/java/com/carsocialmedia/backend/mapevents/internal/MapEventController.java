package com.carsocialmedia.backend.mapevents.internal;

import com.carsocialmedia.backend.mapevents.MapEventsService;
import com.carsocialmedia.backend.mapevents.dto.MapEventAttendeeDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventCategoryDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventPageDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventParticipantDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventPinDto;
import com.carsocialmedia.backend.mapevents.dto.MapEventSummaryDto;
import com.carsocialmedia.backend.mapevents.dto.request.AddOrganizerRequest;
import com.carsocialmedia.backend.mapevents.dto.request.AttendanceRequest;
import com.carsocialmedia.backend.mapevents.dto.request.CoverImageKeyRequest;
import com.carsocialmedia.backend.mapevents.dto.request.CreateMapEventRequest;
import com.carsocialmedia.backend.mapevents.dto.request.ParticipantDecisionRequest;
import com.carsocialmedia.backend.mapevents.dto.request.RegisterCarRequest;
import com.carsocialmedia.backend.mapevents.dto.request.UpdateMapEventRequest;
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

    /** Enters one of the caller's own cars into the event. */
    @PostMapping("/{eventId}/cars")
    @ResponseStatus(HttpStatus.CREATED)
    public MapEventParticipantDto registerCar(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable UUID eventId,
                                              @Valid @RequestBody RegisterCarRequest request) {
        return mapEventsService.registerCar(userId(jwt), eventId, request.carId());
    }

    @DeleteMapping("/{eventId}/cars/{carId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdrawCar(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID eventId,
                            @PathVariable UUID carId) {
        mapEventsService.withdrawCar(userId(jwt), eventId, carId);
    }

    /** An organizer's verdict on an entered car. */
    @PatchMapping("/{eventId}/cars/{carId}")
    public MapEventParticipantDto decideParticipant(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable UUID eventId,
                                                    @PathVariable UUID carId,
                                                    @Valid @RequestBody ParticipantDecisionRequest request) {
        return mapEventsService.decideParticipant(userId(jwt), eventId, carId, request.status());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
