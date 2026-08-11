package com.carsocialmedia.backend.mapevents.internal;

import com.carsocialmedia.backend.mapevents.MapEventsService;
import com.carsocialmedia.backend.mapevents.dto.MapEventPinDto;
import com.carsocialmedia.backend.mapevents.exception.EventClosedException;
import com.carsocialmedia.backend.mapevents.exception.EventNotEditableException;
import com.carsocialmedia.backend.mapevents.exception.MapEventNotFoundException;
import com.carsocialmedia.backend.mapevents.exception.NotEventOrganizerException;
import com.carsocialmedia.backend.testsupport.AppWebMvcTest;
import com.carsocialmedia.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST surface of {@link MapEventController}: the auth requirement, the exact snake_case JSON
 * the mobile app parses (the map screen builds Mapbox markers straight from these field names, so a
 * rename here is a silent client break), query-parameter defaults, and the exception→status
 * mapping.
 */
@AppWebMvcTest(MapEventController.class)
class MapEventControllerWebTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MapEventsService mapEventsService;

    private static MapEventPinDto pin() {
        return new MapEventPinDto(
                EVENT_ID, "Sunday meet", "car_meet", "Car meet",
                46.7874479284246, 23.6308604799099,
                "Iulius Mall parking",
                "https://cdn.example/cover.webp",
                Instant.parse("2026-09-01T17:00:00Z"),
                Instant.parse("2026-09-01T21:00:00Z"),
                "upcoming", 42, 7, 1.8342);
    }

    // ---- auth ---------------------------------------------------------------

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/map-events/nearby?lat=46.77&lng=23.62"))
                .andExpect(status().isUnauthorized());
    }

    // ---- map pins -----------------------------------------------------------

    @Test
    void nearbyReturnsTheSnakeCaseShapeTheMapParses() throws Exception {
        when(mapEventsService.findNearby(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(pin()));

        mockMvc.perform(get("/api/v1/map-events/nearby?lat=46.77&lng=23.62").with(TestJwts.user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$[0].title").value("Sunday meet"))
                .andExpect(jsonPath("$[0].category_id").value("car_meet"))
                .andExpect(jsonPath("$[0].category_label").value("Car meet"))
                .andExpect(jsonPath("$[0].location_name").value("Iulius Mall parking"))
                .andExpect(jsonPath("$[0].cover_image_url").value("https://cdn.example/cover.webp"))
                .andExpect(jsonPath("$[0].starts_at").exists())
                .andExpect(jsonPath("$[0].attendees_count").value(42))
                .andExpect(jsonPath("$[0].attending_cars_count").value(7))
                .andExpect(jsonPath("$[0].distance_km").value(1.8342));
    }

    @Test
    void nearbyAppliesItsRadiusAndLimitDefaults() throws Exception {
        when(mapEventsService.findNearby(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/map-events/nearby?lat=46.77&lng=23.62").with(TestJwts.user(USER_ID)))
                .andExpect(status().isOk());

        verify(mapEventsService).findNearby(46.77, 23.62, 25.0, null, 200);
    }

    @Test
    void nearbyPassesTheCategoryFilterThrough() throws Exception {
        when(mapEventsService.findNearby(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/map-events/nearby?lat=46.77&lng=23.62&category=car_meet&radius_km=10&limit=50")
                        .with(TestJwts.user(USER_ID)))
                .andExpect(status().isOk());

        verify(mapEventsService).findNearby(46.77, 23.62, 10.0, "car_meet", 50);
    }

    // ---- exception mapping --------------------------------------------------

    @Test
    void anUnknownOrUnapprovedEventIs404() throws Exception {
        when(mapEventsService.getEvent(eq(USER_ID), eq(EVENT_ID)))
                .thenThrow(new MapEventNotFoundException(EVENT_ID));

        mockMvc.perform(get("/api/v1/map-events/" + EVENT_ID).with(TestJwts.user(USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void actingOnSomebodyElsesEventIs403() throws Exception {
        doThrow(new NotEventOrganizerException("You do not organize this event"))
                .when(mapEventsService).cancelEvent(eq(USER_ID), eq(EVENT_ID));

        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/cancel").with(TestJwts.user(USER_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void editingAnApprovedEventIs409() throws Exception {
        doThrow(new EventNotEditableException("An approved event can no longer be edited"))
                .when(mapEventsService).markFinished(eq(USER_ID), eq(EVENT_ID));

        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/finish").with(TestJwts.user(USER_ID)))
                .andExpect(status().isConflict());
    }

    @Test
    void rsvpAfterTheEventHasFinishedIs409() throws Exception {
        doThrow(new EventClosedException("This event has finished; RSVP is closed"))
                .when(mapEventsService).setAttendance(eq(USER_ID), eq(EVENT_ID), eq("attending"));

        mockMvc.perform(put("/api/v1/map-events/" + EVENT_ID + "/attendance")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"attending\"}"))
                .andExpect(status().isConflict());
    }

    // ---- request binding ----------------------------------------------------

    @Test
    void deletingAnEventReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/map-events/" + EVENT_ID).with(TestJwts.user(USER_ID)))
                .andExpect(status().isNoContent());

        verify(mapEventsService).deleteEvent(USER_ID, EVENT_ID);
    }

    @Test
    void aBlankRsvpStatusIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(put("/api/v1/map-events/" + EVENT_ID + "/attendance")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creatingAnEventRequiresTheMandatoryFields() throws Exception {
        // Missing title / description / coordinates / start time.
        mockMvc.perform(post("/api/v1/map-events")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_id\":\"car_meet\"}"))
                .andExpect(status().isBadRequest());
    }
}
