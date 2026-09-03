package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.mapevents.MapEventsService;
import com.tweakdapp.backend.mapevents.dto.MapEventDto;
import com.tweakdapp.backend.mapevents.dto.MapEventParticipantDto;
import com.tweakdapp.backend.mapevents.dto.MapEventPinDto;
import com.tweakdapp.backend.mapevents.dto.MapEventViewerStateDto;
import com.tweakdapp.backend.mapevents.dto.MapEventWithdrawalRequestDto;
import com.tweakdapp.backend.mapevents.exception.EventClosedException;
import com.tweakdapp.backend.mapevents.exception.EventNotEditableException;
import com.tweakdapp.backend.mapevents.exception.InvalidMapEventException;
import com.tweakdapp.backend.mapevents.exception.MapEventNotFoundException;
import com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
                "upcoming", 42, 7, 20);
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
                .andExpect(jsonPath("$[0].max_participant_capacity").value(20));
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

    // ---- withdrawal requests --------------------------------------------------

    private static final UUID CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    /** A minimal event page, standing in for whatever {@code reloadAndAssemble} would build. */
    private static MapEventDto sampleEvent() {
        return new MapEventDto(
                EVENT_ID, "Sunday meet", "Come along", "car_meet", "Car meet",
                "Iulius Mall", 46.7712, 23.6236,
                Instant.parse("2026-08-10T10:00:00Z"), null, null,
                "upcoming", "accepted", null,
                true, null, 3, 2,
                List.of(), List.of(), null,
                new MapEventViewerStateDto(false, false, false, null, true, true, List.of(CAR_ID)),
                Instant.parse("2026-08-01T09:00:00Z"));
    }

    // ---- my participation ------------------------------------------------------

    @Test
    void myParticipantsReturnsTheSnakeCaseShapeIncludingTheRejectionReason() throws Exception {
        when(mapEventsService.listMyParticipants(USER_ID, EVENT_ID)).thenReturn(List.of(
                new MapEventParticipantDto(
                        new CarSummaryDto(CAR_ID, "Brand", "Model", 2021, 300, 400, null, null, null),
                        "rejected", Instant.parse("2026-08-01T12:00:00Z"), "not a fit")));

        mockMvc.perform(get("/api/v1/map-events/" + EVENT_ID + "/cars/mine").with(TestJwts.user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("rejected"))
                .andExpect(jsonPath("$[0].rejection_reason").value("not a fit"))
                .andExpect(jsonPath("$[0].car.id").value(CAR_ID.toString()));
    }

    // ---- participating cars -----------------------------------------------------

    @Test
    void registeringACarReturnsTheUpdatedEventNotJustTheEntry() throws Exception {
        when(mapEventsService.registerCar(USER_ID, EVENT_ID, CAR_ID)).thenReturn(sampleEvent());

        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/cars")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"car_id\":\"" + CAR_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.attending_cars_count").value(2))
                .andExpect(jsonPath("$.viewer.my_registered_car_ids[0]").value(CAR_ID.toString()));
    }

    @Test
    void withdrawingACarReturnsTheUpdatedEvent() throws Exception {
        when(mapEventsService.withdrawCar(USER_ID, EVENT_ID, CAR_ID)).thenReturn(sampleEvent());

        mockMvc.perform(delete("/api/v1/map-events/" + EVENT_ID + "/cars/" + CAR_ID)
                        .with(TestJwts.user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()));
    }

    @Test
    void decidingOnAnEnteredCarReturnsTheUpdatedEvent() throws Exception {
        when(mapEventsService.decideParticipant(USER_ID, EVENT_ID, CAR_ID, "rejected", "not a fit"))
                .thenReturn(sampleEvent());

        mockMvc.perform(patch("/api/v1/map-events/" + EVENT_ID + "/cars/" + CAR_ID)
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"rejected\",\"reason\":\"not a fit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()));
    }

    @Test
    void requestingWithdrawalReturnsTheUpdatedEvent() throws Exception {
        when(mapEventsService.requestWithdrawal(eq(USER_ID), eq(EVENT_ID), any()))
                .thenReturn(sampleEvent());

        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/withdraw")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"can't make it\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.attending_cars_count").value(2));

        verify(mapEventsService).requestWithdrawal(USER_ID, EVENT_ID, "can't make it");
    }

    @Test
    void requestingWithdrawalWithNoAcceptedRegistrationIs400() throws Exception {
        doThrow(new InvalidMapEventException("You have no accepted registration to withdraw from this event"))
                .when(mapEventsService).requestWithdrawal(eq(USER_ID), eq(EVENT_ID), any());

        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/withdraw")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aWithdrawalNoteOverTheLengthLimitIsRejectedBeforeReachingTheService() throws Exception {
        String tooLong = "x".repeat(1001);

        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/withdraw")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listingWithdrawalRequestsReturnsTheSnakeCaseShape() throws Exception {
        when(mapEventsService.listWithdrawalRequests(USER_ID, EVENT_ID)).thenReturn(List.of(
                new MapEventWithdrawalRequestDto(
                        new ProfileSearchResultDto(OWNER_ID, "Owner Name", "owner_username", null),
                        List.of(new CarSummaryDto(CAR_ID, "Brand", "Model", 2021, 300, 400, null, null, null)),
                        "moving away")));

        mockMvc.perform(get("/api/v1/map-events/" + EVENT_ID + "/withdrawals").with(TestJwts.user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].owner.id").value(OWNER_ID.toString()))
                .andExpect(jsonPath("$[0].owner.username").value("owner_username"))
                .andExpect(jsonPath("$[0].cars[0].id").value(CAR_ID.toString()))
                .andExpect(jsonPath("$[0].note").value("moving away"));
    }

    @Test
    void listingWithdrawalRequestsAsANonOrganizerIs403() throws Exception {
        doThrow(new NotEventOrganizerException("You do not organize this event"))
                .when(mapEventsService).listWithdrawalRequests(USER_ID, EVENT_ID);

        mockMvc.perform(get("/api/v1/map-events/" + EVENT_ID + "/withdrawals").with(TestJwts.user(USER_ID)))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvingAWithdrawalReturnsTheUpdatedEvent() throws Exception {
        when(mapEventsService.approveWithdrawal(USER_ID, EVENT_ID, OWNER_ID)).thenReturn(sampleEvent());

        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/withdrawals/" + OWNER_ID + "/approve")
                        .with(TestJwts.user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()));

        verify(mapEventsService).approveWithdrawal(USER_ID, EVENT_ID, OWNER_ID);
    }

    @Test
    void rejectingAWithdrawalReturnsTheUpdatedEvent() throws Exception {
        when(mapEventsService.rejectWithdrawal(USER_ID, EVENT_ID, OWNER_ID)).thenReturn(sampleEvent());

        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/withdrawals/" + OWNER_ID + "/reject")
                        .with(TestJwts.user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVENT_ID.toString()));
    }

    @Test
    void decidingAWithdrawalRequestThatDoesNotExistIs400() throws Exception {
        doThrow(new InvalidMapEventException("No pending withdrawal request from that participant"))
                .when(mapEventsService).approveWithdrawal(USER_ID, EVENT_ID, OWNER_ID);

        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/withdrawals/" + OWNER_ID + "/approve")
                        .with(TestJwts.user(USER_ID)))
                .andExpect(status().isBadRequest());
    }

    // ---- rules ----------------------------------------------------------------

    @Test
    void replacingRulesPassesTheOrderedListThrough() throws Exception {
        mockMvc.perform(put("/api/v1/map-events/" + EVENT_ID + "/rules")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":[\"No burnouts\",\"Park in marked bays\"]}"))
                .andExpect(status().isOk());

        verify(mapEventsService).replaceRules(USER_ID, EVENT_ID, List.of("No burnouts", "Park in marked bays"));
    }

    @Test
    void replacingRulesWithAnEmptyListIsAccepted() throws Exception {
        mockMvc.perform(put("/api/v1/map-events/" + EVENT_ID + "/rules")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":[]}"))
                .andExpect(status().isOk());

        verify(mapEventsService).replaceRules(USER_ID, EVENT_ID, List.of());
    }

    @Test
    void aBlankRuleIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(put("/api/v1/map-events/" + EVENT_ID + "/rules")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":[\"\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void replacingRulesOnAnApprovedEventIs409() throws Exception {
        doThrow(new EventNotEditableException("An approved event can no longer be edited"))
                .when(mapEventsService).replaceRules(eq(USER_ID), eq(EVENT_ID), any());

        mockMvc.perform(put("/api/v1/map-events/" + EVENT_ID + "/rules")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":[\"No burnouts\"]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void replacingRulesAsANonOrganizerIs403() throws Exception {
        doThrow(new NotEventOrganizerException("You do not organize this event"))
                .when(mapEventsService).replaceRules(eq(USER_ID), eq(EVENT_ID), any());

        mockMvc.perform(put("/api/v1/map-events/" + EVENT_ID + "/rules")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rules\":[\"No burnouts\"]}"))
                .andExpect(status().isForbidden());
    }
}
