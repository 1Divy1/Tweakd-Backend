package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.mapevents.MapEventsService;
import com.tweakdapp.backend.mapevents.dto.PublicMapEventDto;
import com.tweakdapp.backend.mapevents.dto.PublicMapEventOrganizerDto;
import com.tweakdapp.backend.mapevents.exception.MapEventGoneException;
import com.tweakdapp.backend.mapevents.exception.MapEventNotFoundException;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public event page's API — the second unauthenticated route, after the public car.
 *
 * <p>The first test is the load-bearing one: a friend tapping a link in a group chat has no JWT.
 * The rest pin what an anonymous caller gets: no ids of any kind, snake_case the Worker parses, a
 * 410 a crawler can tell apart from a 404, and cache headers.
 */
@AppWebMvcTest(PublicMapEventController.class)
class PublicMapEventControllerWebTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String BASE = "/public/v1/events/";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MapEventsService mapEventsService;

    private static PublicMapEventDto page() {
        return new PublicMapEventDto(
                "Sunday meet", "Coffee and cars.", "Car meet", "Iulius Mall",
                46.7712, 23.6236,
                Instant.parse("2026-10-04T09:00:00Z"), Instant.parse("2026-10-04T13:00:00Z"),
                "https://media.tweakdapp.com/events/cover.webp",
                "upcoming", 42, 12,
                List.of(new PublicMapEventOrganizerDto("individual", "creator", "Dave", "dave",
                        "https://avatars.tweakdapp.com/dave.jpg")),
                List.of("No burnouts"));
    }

    @Test
    void theEventIsServedWithNoAuthenticationAtAll() throws Exception {
        when(mapEventsService.getPublicEvent(EVENT_ID)).thenReturn(page());

        mockMvc.perform(get(BASE + EVENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Sunday meet"));
    }

    @Test
    void theWireShapeIsSnakeCase() throws Exception {
        when(mapEventsService.getPublicEvent(EVENT_ID)).thenReturn(page());

        mockMvc.perform(get(BASE + EVENT_ID))
                .andExpect(jsonPath("$.category_label").value("Car meet"))
                .andExpect(jsonPath("$.location_name").value("Iulius Mall"))
                .andExpect(jsonPath("$.lat").value(46.7712))
                .andExpect(jsonPath("$.starts_at").exists())
                .andExpect(jsonPath("$.ends_at").exists())
                .andExpect(jsonPath("$.cover_image_url").value("https://media.tweakdapp.com/events/cover.webp"))
                .andExpect(jsonPath("$.phase").value("upcoming"))
                .andExpect(jsonPath("$.attendees_count").value(42))
                .andExpect(jsonPath("$.attending_cars_count").value(12))
                .andExpect(jsonPath("$.organizers[0].username").value("dave"))
                .andExpect(jsonPath("$.organizers[0].image_url").value("https://avatars.tweakdapp.com/dave.jpg"))
                .andExpect(jsonPath("$.rules[0]").value("No burnouts"));
    }

    /** Nothing internal on the open internet: no event, organizer or profile ids, no approval state. */
    @Test
    void theResponseCarriesNoInternalState() throws Exception {
        when(mapEventsService.getPublicEvent(EVENT_ID)).thenReturn(page());

        mockMvc.perform(get(BASE + EVENT_ID))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.approval_status").doesNotExist())
                .andExpect(jsonPath("$.rejection_reason").doesNotExist())
                .andExpect(jsonPath("$.viewer").doesNotExist())
                .andExpect(jsonPath("$.created_by").doesNotExist())
                .andExpect(jsonPath("$.organizers[0].id").doesNotExist())
                .andExpect(jsonPath("$.organizers[0].reference_id").doesNotExist());
    }

    @Test
    void theResponseIsCacheable() throws Exception {
        when(mapEventsService.getPublicEvent(EVENT_ID)).thenReturn(page());

        mockMvc.perform(get(BASE + EVENT_ID))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=60, s-maxage=300"));
    }

    @Test
    void aCancelledEventIsGoneAndAnUnknownOneIsNotFound() throws Exception {
        when(mapEventsService.getPublicEvent(EVENT_ID)).thenThrow(new MapEventGoneException());
        mockMvc.perform(get(BASE + EVENT_ID)).andExpect(status().isGone());

        UUID unknown = UUID.randomUUID();
        when(mapEventsService.getPublicEvent(unknown)).thenThrow(new MapEventNotFoundException(unknown));
        mockMvc.perform(get(BASE + unknown)).andExpect(status().isNotFound());
    }

    /** A scraper walking garbage ids never reaches the service, let alone the database. */
    @Test
    void aMalformedIdIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(get(BASE + "not-a-uuid")).andExpect(status().is4xxClientError());
        verify(mapEventsService, never()).getPublicEvent(any());
    }
}
