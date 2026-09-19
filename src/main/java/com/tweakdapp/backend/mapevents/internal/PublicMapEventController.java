package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.mapevents.MapEventsService;
import com.tweakdapp.backend.mapevents.dto.PublicMapEventDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The event behind a shared link, for someone who does not have the app.
 *
 * <p>Unauthenticated (it sits under {@code /public/**}, see {@code SecurityConfig}) and called by
 * the Tweakd-Web-App Worker at the edge, never by a browser — so, like the public car route, it
 * needs no CORS. Everything here assumes hostile traffic: the response is a hand-written
 * projection, there is no caller identity, and a malformed id is rejected by Spring's UUID
 * conversion (400) before a query runs.
 *
 * <p>There is no view counting and no {@code ?s=} parameter: unlike a car, an event's link has
 * no owner to report numbers to.
 */
@RestController
@RequestMapping("/public/v1/events")
public class PublicMapEventController {

    private final MapEventsService mapEventsService;

    public PublicMapEventController(MapEventsService mapEventsService) {
        this.mapEventsService = mapEventsService;
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<PublicMapEventDto> getPublicEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok()
                // The same trade as the public car: short in the browser, five minutes at the edge,
                // because one link doing well in a group chat is otherwise one database round trip
                // per tap against a two-connection pool. Head-counts lag by that much at worst.
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=60, s-maxage=300")
                .body(mapEventsService.getPublicEvent(eventId));
    }
}
