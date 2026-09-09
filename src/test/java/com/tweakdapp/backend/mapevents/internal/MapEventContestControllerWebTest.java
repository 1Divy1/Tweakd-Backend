package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.garage.dto.CarOwnerDto;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.mapevents.MapEventContestsService;
import com.tweakdapp.backend.mapevents.dto.CarEventHistoryEventDto;
import com.tweakdapp.backend.mapevents.dto.CarEventHistoryItemDto;
import com.tweakdapp.backend.mapevents.dto.CarEventPlacementDto;
import com.tweakdapp.backend.mapevents.dto.ContestCategoryDto;
import com.tweakdapp.backend.mapevents.dto.ContestDto;
import com.tweakdapp.backend.mapevents.dto.ContestEntryDto;
import com.tweakdapp.backend.mapevents.dto.ContestMyEntryDto;
import com.tweakdapp.backend.mapevents.dto.ContestViewerStateDto;
import com.tweakdapp.backend.mapevents.exception.ContestClosedException;
import com.tweakdapp.backend.mapevents.exception.ContestNotFoundException;
import com.tweakdapp.backend.mapevents.exception.ContestNotOpenException;
import com.tweakdapp.backend.mapevents.exception.NotEligibleToVoteException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST surface of {@link MapEventContestController}: the auth requirement, the exact
 * snake_case JSON the app parses, request validation, and the exception → status mapping.
 */
@AppWebMvcTest(MapEventContestController.class)
class MapEventContestControllerWebTest {

    private static final UUID EVENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID CONTEST_ID = UUID.fromString("11111111-2222-3333-4444-666666666666");
    private static final UUID CAR_ID = UUID.fromString("11111111-2222-3333-4444-777777777777");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MapEventContestsService contestsService;

    private static ContestDto contest() {
        CarSummaryDto car = new CarSummaryDto(CAR_ID, "BMW", "M4 Competition", 2023, 503, 650, null, null,
                new CarOwnerDto(USER_ID, "torque_sasha"));
        return new ContestDto(
                CONTEST_ID, EVENT_ID,
                new ContestCategoryDto("exhaust", "Best exhaust system", "exhaust"),
                "Best exhaust system", "Note and build quality.",
                "open",
                Instant.parse("2026-08-11T18:30:00Z"), Instant.parse("2026-08-11T22:30:00Z"),
                null, false,
                186, 6,
                List.of(new ContestEntryDto(car, 61, 1, null, Instant.parse("2026-08-11T20:00:00Z"))),
                new ContestViewerStateDto(false, true, CAR_ID, false,
                        List.of(new ContestMyEntryDto(CAR_ID, "accepted", null))),
                List.of(),
                new ProfileSearchResultDto(USER_ID, "Sasha", "torque_sasha", null),
                Instant.parse("2026-08-11T17:00:00Z"));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/map-events/" + EVENT_ID + "/contests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsTheSnakeCaseShapeTheAppParses() throws Exception {
        when(contestsService.listContests(USER_ID, EVENT_ID)).thenReturn(List.of(contest()));

        mockMvc.perform(get("/api/v1/map-events/" + EVENT_ID + "/contests").with(TestJwts.user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(CONTEST_ID.toString()))
                .andExpect(jsonPath("$[0].event_id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$[0].category.id").value("exhaust"))
                .andExpect(jsonPath("$[0].category.icon").value("exhaust"))
                .andExpect(jsonPath("$[0].status").value("open"))
                .andExpect(jsonPath("$[0].opens_at").value("2026-08-11T18:30:00Z"))
                .andExpect(jsonPath("$[0].closes_at").value("2026-08-11T22:30:00Z"))
                .andExpect(jsonPath("$[0].finished_early").value(false))
                .andExpect(jsonPath("$[0].votes_count").value(186))
                .andExpect(jsonPath("$[0].entries_count").value(6))
                .andExpect(jsonPath("$[0].entries[0].car.id").value(CAR_ID.toString()))
                .andExpect(jsonPath("$[0].entries[0].car.owner.username").value("torque_sasha"))
                .andExpect(jsonPath("$[0].entries[0].votes_count").value(61))
                .andExpect(jsonPath("$[0].entries[0].rank").value(1))
                .andExpect(jsonPath("$[0].viewer.can_vote").value(true))
                .andExpect(jsonPath("$[0].viewer.vote_car_id").value(CAR_ID.toString()))
                .andExpect(jsonPath("$[0].viewer.my_entries[0].status").value("accepted"))
                .andExpect(jsonPath("$[0].pending_entries").isEmpty())
                .andExpect(jsonPath("$[0].created_by.username").value("torque_sasha"));
    }

    @Test
    void voteCarriesTheCarIdAndMapsEligibilityTo403() throws Exception {
        when(contestsService.vote(USER_ID, EVENT_ID, CONTEST_ID, CAR_ID)).thenReturn(contest());

        mockMvc.perform(put("/api/v1/map-events/" + EVENT_ID + "/contests/" + CONTEST_ID + "/vote")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"car_id\":\"" + CAR_ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewer.vote_car_id").value(CAR_ID.toString()));

        when(contestsService.vote(USER_ID, EVENT_ID, CONTEST_ID, CAR_ID))
                .thenThrow(new NotEligibleToVoteException("You can't vote for your own car"));
        mockMvc.perform(put("/api/v1/map-events/" + EVENT_ID + "/contests/" + CONTEST_ID + "/vote")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"car_id\":\"" + CAR_ID + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can't vote for your own car"));
    }

    @Test
    void conflictsAndMissingContestsMapToTheirStatuses() throws Exception {
        when(contestsService.finishContest(USER_ID, EVENT_ID, CONTEST_ID))
                .thenThrow(new ContestNotOpenException("Voting hasn't opened yet"));
        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/contests/" + CONTEST_ID + "/finish")
                        .with(TestJwts.user(USER_ID)))
                .andExpect(status().isConflict());

        when(contestsService.requestEntry(USER_ID, EVENT_ID, CONTEST_ID, CAR_ID))
                .thenThrow(new ContestClosedException("This contest has finished"));
        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/contests/" + CONTEST_ID + "/entries")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"car_id\":\"" + CAR_ID + "\"}"))
                .andExpect(status().isConflict());

        when(contestsService.getContest(USER_ID, EVENT_ID, CONTEST_ID))
                .thenThrow(new ContestNotFoundException(CONTEST_ID));
        mockMvc.perform(get("/api/v1/map-events/" + EVENT_ID + "/contests/" + CONTEST_ID)
                        .with(TestJwts.user(USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createValidatesTheBodyAndReturns201() throws Exception {
        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/contests")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_id\":\"exhaust\",\"title\":\"ab\","
                                + "\"opens_at\":\"2026-08-11T18:30:00Z\",\"closes_at\":\"2026-08-11T22:30:00Z\"}"))
                .andExpect(status().isBadRequest());

        when(contestsService.createContest(eq(USER_ID), eq(EVENT_ID), any())).thenReturn(contest());
        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/contests")
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category_id\":\"exhaust\",\"title\":\"Best exhaust system\","
                                + "\"criteria\":\"Note and build quality.\","
                                + "\"opens_at\":\"2026-08-11T18:30:00Z\",\"closes_at\":\"2026-08-11T22:30:00Z\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CONTEST_ID.toString()));
    }

    @Test
    void openStartsVotingThroughItsOwnEndpoint() throws Exception {
        when(contestsService.openContest(USER_ID, EVENT_ID, CONTEST_ID)).thenReturn(contest());

        mockMvc.perform(post("/api/v1/map-events/" + EVENT_ID + "/contests/" + CONTEST_ID + "/open")
                        .with(TestJwts.user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CONTEST_ID.toString()));

        verify(contestsService).openContest(USER_ID, EVENT_ID, CONTEST_ID);
    }

    @Test
    void decisionsPassTheStatusAndReasonThrough() throws Exception {
        when(contestsService.decideEntry(USER_ID, EVENT_ID, CONTEST_ID, CAR_ID, "rejected", "Too quiet"))
                .thenReturn(contest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/map-events/" + EVENT_ID + "/contests/" + CONTEST_ID + "/entries/" + CAR_ID)
                        .with(TestJwts.user(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"rejected\",\"reason\":\"Too quiet\"}"))
                .andExpect(status().isOk());

        verify(contestsService).decideEntry(USER_ID, EVENT_ID, CONTEST_ID, CAR_ID, "rejected", "Too quiet");
    }

    @Test
    void carHistoryReturnsPlacementsInSnakeCase() throws Exception {
        when(contestsService.getCarHistory(USER_ID, CAR_ID)).thenReturn(List.of(new CarEventHistoryItemDto(
                new CarEventHistoryEventDto(EVENT_ID, "Casino Square", null, "Place du Casino",
                        Instant.parse("2026-08-11T18:00:00Z"), Instant.parse("2026-08-11T23:00:00Z"), "previous"),
                "accepted",
                List.of(new CarEventPlacementDto(CONTEST_ID, "Best paint / wrap",
                        new ContestCategoryDto("paint", "Best paint / wrap", "paint"),
                        1, 78, 204, Instant.parse("2026-08-11T20:30:00Z"))))));

        mockMvc.perform(get("/api/v1/map-events/cars/" + CAR_ID + "/history").with(TestJwts.user(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].event.id").value(EVENT_ID.toString()))
                .andExpect(jsonPath("$[0].event.location_name").value("Place du Casino"))
                .andExpect(jsonPath("$[0].entry_status").value("accepted"))
                .andExpect(jsonPath("$[0].placements[0].final_rank").value(1))
                .andExpect(jsonPath("$[0].placements[0].final_votes_count").value(78))
                .andExpect(jsonPath("$[0].placements[0].contest_votes_count").value(204))
                .andExpect(jsonPath("$[0].placements[0].category.icon").value("paint"));
    }
}
