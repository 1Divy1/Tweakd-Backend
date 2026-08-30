package com.tweakdapp.backend.admin.internal.controllers;

import com.tweakdapp.backend.admin.internal.AdminAccessService;
import com.tweakdapp.backend.admin.internal.Capability;
import com.tweakdapp.backend.admin.internal.dto.EventRejectionRequest;
import com.tweakdapp.backend.mapevents.MapEventsService;
import com.tweakdapp.backend.mapevents.dto.MapEventDto;
import com.tweakdapp.backend.mapevents.dto.MapEventPageDto;
import com.tweakdapp.backend.mapevents.dto.MapEventSummaryDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * The Map Events review page: user-submitted car events wait here until someone puts them on the
 * map. Every endpoint needs {@code APPROVE_EVENTS}, which only {@code owner} and
 * {@code senior_admin} hold — content moderators handle reported content, not publishing.
 *
 * <p>All of it delegates to {@code mapevents}; this module owns the capability check and nothing
 * else.
 */
@RestController
@RequestMapping("/api/v1/admin/map-events")
class AdminMapEventsController {

    private final AdminAccessService access;
    private final MapEventsService mapEventsService;

    AdminMapEventsController(AdminAccessService access, MapEventsService mapEventsService) {
        this.access = access;
        this.mapEventsService = mapEventsService;
    }

    /**
     * One keyset page of the review queue, <strong>oldest submission first</strong> so nothing
     * waits indefinitely. {@code status} defaults to {@code pending}; pass {@code accepted} or
     * {@code rejected} to audit past decisions.
     */
    @GetMapping
    public MapEventPageDto<MapEventSummaryDto> listForReview(@AuthenticationPrincipal Jwt jwt,
                                                              @RequestParam(required = false) String status,
                                                              @RequestParam(required = false) String cursor,
                                                              @RequestParam(defaultValue = "20") int size) {
        access.require(userId(jwt), Capability.APPROVE_EVENTS);
        return mapEventsService.listEventsForReview(status, cursor, size);
    }

    /** How many events are waiting for review — the dashboard badge. */
    @GetMapping("/counts")
    public Map<String, Long> counts(@AuthenticationPrincipal Jwt jwt) {
        access.require(userId(jwt), Capability.APPROVE_EVENTS);
        return Map.of("pending", mapEventsService.countPendingReview());
    }

    /** Full detail of one submission, whatever its approval state. */
    @GetMapping("/{eventId}")
    public MapEventDto getEvent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        access.require(userId(jwt), Capability.APPROVE_EVENTS);
        return mapEventsService.getEventAsAdmin(eventId);
    }

    /** Approves the event, putting it on the map and locking it against further organizer edits. */
    @PostMapping("/{eventId}/approve")
    public MapEventDto approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        access.require(userId(jwt), Capability.APPROVE_EVENTS);
        return mapEventsService.approveEvent(eventId);
    }

    /** Rejects the event with a reason the creator can act on; editing it resubmits it. */
    @PostMapping("/{eventId}/reject")
    public MapEventDto reject(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID eventId,
                              @Valid @RequestBody EventRejectionRequest request) {
        access.require(userId(jwt), Capability.APPROVE_EVENTS);
        return mapEventsService.rejectEvent(eventId, request.reason());
    }

    /** Deletes any event outright — child rows cascade and the cover is removed from R2. */
    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        access.require(userId(jwt), Capability.APPROVE_EVENTS);
        mapEventsService.deleteEventAsAdmin(eventId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
