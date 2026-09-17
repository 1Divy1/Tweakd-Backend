package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.mapevents.dto.ParticipantCardDto;

import com.tweakdapp.backend.mapevents.MapEventContestsService;
import com.tweakdapp.backend.mapevents.dto.CarEventHistoryItemDto;
import com.tweakdapp.backend.mapevents.dto.ContestCategoryDto;
import com.tweakdapp.backend.mapevents.dto.ContestDto;
import com.tweakdapp.backend.mapevents.dto.request.ContestEntryDecisionRequest;
import com.tweakdapp.backend.mapevents.dto.request.ContestEntryRequest;
import com.tweakdapp.backend.mapevents.dto.request.ContestVoteRequest;
import com.tweakdapp.backend.mapevents.dto.request.CreateContestRequest;
import com.tweakdapp.backend.mapevents.dto.request.UpdateContestRequest;
import com.tweakdapp.backend.shared.ratelimit.RateLimited;
import com.tweakdapp.backend.shared.ratelimit.RateLimits;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Contests inside a car event. Every write answers with the fresh {@link ContestDto}, so the app
 * never follows a write with a read.
 */
@RestController
@RequestMapping("/api/v1/map-events")
class MapEventContestController {

    private final MapEventContestsService contestsService;

    MapEventContestController(MapEventContestsService contestsService) {
        this.contestsService = contestsService;
    }

    /** The categories an organizer may pick, in display order. */
    @GetMapping("/contest-categories")
    public List<ContestCategoryDto> listCategories() {
        return contestsService.listCategories();
    }

    /**
     * The caller's participant cards for an event, one per car they had accepted into it. Empty
     * until an organizer marks the event finished — only then does a card exist.
     */
    @GetMapping("/{eventId}/cards")
    public List<ParticipantCardDto> listMyParticipantCards(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID eventId) {
        return contestsService.listMyParticipantCards(userId(jwt), eventId);
    }

    /** Every contest of an event with its ranked ballot — one read paints the contests tab. */
    @GetMapping("/{eventId}/contests")
    public List<ContestDto> listContests(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
        return contestsService.listContests(userId(jwt), eventId);
    }

    @GetMapping("/{eventId}/contests/{contestId}")
    public ContestDto getContest(@AuthenticationPrincipal Jwt jwt,
                                 @PathVariable UUID eventId,
                                 @PathVariable UUID contestId) {
        return contestsService.getContest(userId(jwt), eventId, contestId);
    }

    /** Creates and publishes a contest. Organizer only. */
    @PostMapping("/{eventId}/contests")
    @RateLimited(RateLimits.MAPEVENTS_CREATE)
    @ResponseStatus(HttpStatus.CREATED)
    public ContestDto createContest(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID eventId,
                                    @Valid @RequestBody CreateContestRequest request) {
        return contestsService.createContest(userId(jwt), eventId, request);
    }

    /** Partial update; while open only the closing time and the judging note may change. */
    @PatchMapping("/{eventId}/contests/{contestId}")
    public ContestDto updateContest(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID eventId,
                                    @PathVariable UUID contestId,
                                    @Valid @RequestBody UpdateContestRequest request) {
        return contestsService.updateContest(userId(jwt), eventId, contestId, request);
    }

    /** Opens voting now. Idempotent. */
    @PostMapping("/{eventId}/contests/{contestId}/open")
    public ContestDto openContest(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID eventId,
                                  @PathVariable UUID contestId) {
        return contestsService.openContest(userId(jwt), eventId, contestId);
    }

    /** Finishes voting now and publishes the result. Idempotent. */
    @PostMapping("/{eventId}/contests/{contestId}/finish")
    public ContestDto finishContest(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID eventId,
                                    @PathVariable UUID contestId) {
        return contestsService.finishContest(userId(jwt), eventId, contestId);
    }

    /** Deletes a contest that has not opened yet. */
    @DeleteMapping("/{eventId}/contests/{contestId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContest(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID eventId,
                              @PathVariable UUID contestId) {
        contestsService.deleteContest(userId(jwt), eventId, contestId);
    }

    /** Asks to enter one of the caller's accepted event cars into the contest. */
    @PostMapping("/{eventId}/contests/{contestId}/entries")
    @RateLimited(RateLimits.MAPEVENTS_PARTICIPATE)
    public ContestDto requestEntry(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID eventId,
                                   @PathVariable UUID contestId,
                                   @Valid @RequestBody ContestEntryRequest request) {
        return contestsService.requestEntry(userId(jwt), eventId, contestId, request.carId());
    }

    /** Withdraws the caller's car: a pending request outright, an accepted entry only while scheduled. */
    @DeleteMapping("/{eventId}/contests/{contestId}/entries/{carId}")
    public ContestDto withdrawEntry(@AuthenticationPrincipal Jwt jwt,
                                    @PathVariable UUID eventId,
                                    @PathVariable UUID contestId,
                                    @PathVariable UUID carId) {
        return contestsService.withdrawEntry(userId(jwt), eventId, contestId, carId);
    }

    /** An organizer's verdict on an entry request. Rejecting needs a reason. */
    @PatchMapping("/{eventId}/contests/{contestId}/entries/{carId}")
    public ContestDto decideEntry(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID eventId,
                                  @PathVariable UUID contestId,
                                  @PathVariable UUID carId,
                                  @Valid @RequestBody ContestEntryDecisionRequest request) {
        return contestsService.decideEntry(userId(jwt), eventId, contestId, carId, request.status(), request.reason());
    }

    /** Casts or changes the caller's vote. */
    @PutMapping("/{eventId}/contests/{contestId}/vote")
    @RateLimited(RateLimits.MAPEVENTS_PARTICIPATE)
    public ContestDto vote(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID eventId,
                           @PathVariable UUID contestId,
                           @Valid @RequestBody ContestVoteRequest request) {
        return contestsService.vote(userId(jwt), eventId, contestId, request.carId());
    }

    /** A car's attended events with any podium places — the car page's history section. */
    @GetMapping("/cars/{carId}/history")
    public List<CarEventHistoryItemDto> getCarHistory(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID carId) {
        return contestsService.getCarHistory(userId(jwt), carId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
