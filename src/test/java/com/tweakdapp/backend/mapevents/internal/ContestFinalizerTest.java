package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.BadgeTrigger;
import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarOwnerDto;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.mapevents.ContestFinishedEvent;
import com.tweakdapp.backend.mapevents.ContestOpenedEvent;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryId;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventEntity;
import com.tweakdapp.backend.mapevents.internal.realtime.ContestBoardPublisher;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestEntryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventAttendeeRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventParticipantRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRepository;
import com.tweakdapp.backend.reputation.ReputationReasons;
import com.tweakdapp.backend.reputation.ReputationService;
import com.tweakdapp.backend.reputation.ReputationSourceType;
import com.tweakdapp.backend.reputation.dto.ReputationSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules that decide who wins and what they get: the ordering with its tie-break, the podium's
 * one-vote floor, exactly which reputation reason and badge trigger each place earns, that a
 * cancelled meet pays nothing, and that finalising is idempotent. Nothing here runs on a timer:
 * every transition below is one an organizer (or the event's own finish) asked for.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContestFinalizerTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CONTEST_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID ORGANIZER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OWNER_A = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID OWNER_B = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID OWNER_C = UUID.fromString("00000000-0000-0000-0000-0000000000d3");
    private static final UUID OWNER_D = UUID.fromString("00000000-0000-0000-0000-0000000000d4");
    private static final UUID CAR_A = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CAR_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID CAR_C = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    private static final UUID CAR_D = UUID.fromString("00000000-0000-0000-0000-0000000000b4");
    private static final UUID SPECTATOR = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    private static final Instant NOW = Instant.parse("2026-08-11T21:12:00Z");

    @Mock private ContestRepository contestRepository;
    @Mock private ContestEntryRepository entryRepository;
    @Mock private MapEventRepository eventRepository;
    @Mock private MapEventAttendeeRepository attendeeRepository;
    @Mock private MapEventParticipantRepository participantRepository;
    @Mock private GarageService garageService;
    @Mock private ReputationService reputationService;
    @Mock private BadgeService badgeService;
    @Mock private ApplicationEventPublisher events;
    @Mock private ContestBoardPublisher boardPublisher;

    private ContestFinalizer finalizer;
    private MapEventEntity event;

    @BeforeEach
    void setUp() {
        finalizer = new ContestFinalizer(contestRepository, entryRepository, eventRepository, attendeeRepository,
                participantRepository, garageService, reputationService, badgeService, events, boardPublisher);

        event = new MapEventEntity();
        event.setId(EVENT_ID);
        event.setTitle("Casino Square Cars & Coffee");
        event.setStatus(MapEventEntity.STATUS_LIVE);
        event.setApprovalStatus(MapEventEntity.APPROVAL_ACCEPTED);
        event.setStartsAt(NOW.minus(3, ChronoUnit.HOURS));
        event.setEndsAt(NOW.plus(2, ChronoUnit.HOURS));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(attendeeRepository.findUserIdsByEventIdAndStatus(EVENT_ID, "attending")).thenReturn(List.of(SPECTATOR, OWNER_A));
        when(garageService.findCarsByIds(any())).thenAnswer(inv -> ((List<UUID>) inv.getArgument(0)).stream()
                .map(id -> new CarSummaryDto(id, "BMW", "M4", 2023, 503, 650, null, null, new CarOwnerDto(OWNER_A, "sasha")))
                .toList());
    }

    private static ContestEntity openContest() {
        ContestEntity contest = new ContestEntity();
        contest.setId(CONTEST_ID);
        contest.setEventId(EVENT_ID);
        contest.setTitle("Best exhaust system");
        contest.setStatus(ContestEntity.STATUS_OPEN);
        contest.setOpensAt(NOW.minus(1, ChronoUnit.HOURS));
        contest.setClosesAt(NOW.plus(1, ChronoUnit.HOURS));
        return contest;
    }

    private static ContestEntryEntity entry(UUID carId, UUID ownerId, int votes, Instant lastVoteAt) {
        ContestEntryEntity e = new ContestEntryEntity();
        e.setId(new ContestEntryId(CONTEST_ID, carId));
        e.setOwnerId(ownerId);
        e.setStatus(ContestEntryEntity.ACCEPTED);
        ReflectionTestUtils.setField(e, "votesCount", votes);
        ReflectionTestUtils.setField(e, "lastVoteAt", lastVoteAt);
        return e;
    }

    // ---- ordering -----------------------------------------------------------

    @Test
    void rankOrdersByVotesThenByWhoReachedTheCountFirstThenByCarId() {
        ContestEntryEntity early = entry(CAR_B, OWNER_B, 5, NOW.minus(30, ChronoUnit.MINUTES));
        ContestEntryEntity late = entry(CAR_A, OWNER_A, 5, NOW.minus(5, ChronoUnit.MINUTES));
        ContestEntryEntity fewer = entry(CAR_C, OWNER_C, 2, NOW.minus(1, ChronoUnit.MINUTES));
        ContestEntryEntity none = entry(CAR_D, OWNER_D, 0, null);

        List<ContestEntryEntity> ranked = ContestFinalizer.rank(List.of(none, late, fewer, early));

        assertThat(ranked).extracting(e -> e.getId().getCarId()).containsExactly(CAR_B, CAR_A, CAR_C, CAR_D);
    }

    // ---- finalising ---------------------------------------------------------

    @Test
    void finalizingFreezesRanksAndPaysOnlyPodiumPlacesWithAtLeastOneVote() {
        ContestEntity contest = openContest();
        ContestEntryEntity first = entry(CAR_A, OWNER_A, 7, NOW.minus(10, ChronoUnit.MINUTES));
        ContestEntryEntity second = entry(CAR_B, OWNER_B, 3, NOW.minus(20, ChronoUnit.MINUTES));
        ContestEntryEntity third = entry(CAR_C, OWNER_C, 3, NOW.minus(2, ChronoUnit.MINUTES));
        ContestEntryEntity fourth = entry(CAR_D, OWNER_D, 0, null);
        when(entryRepository.findByIdContestIdAndStatus(CONTEST_ID, ContestEntryEntity.ACCEPTED))
                .thenReturn(List.of(fourth, third, first, second));

        boolean done = finalizer.finalizeLocked(contest, event, ORGANIZER, true, NOW);

        assertThat(done).isTrue();
        assertThat(contest.getStatus()).isEqualTo(ContestEntity.STATUS_FINISHED);
        assertThat(contest.getFinishedAt()).isEqualTo(NOW);
        assertThat(contest.isFinishedEarly()).isTrue();
        assertThat(contest.getFinishedBy()).isEqualTo(ORGANIZER);
        assertThat(first.getFinalRank()).isEqualTo((short) 1);
        assertThat(second.getFinalRank()).isEqualTo((short) 2);
        assertThat(third.getFinalRank()).isEqualTo((short) 3);
        assertThat(fourth.getFinalRank()).isEqualTo((short) 4);
        assertThat(first.getFinalVotesCount()).isEqualTo(7);

        ReputationSource source = new ReputationSource(ReputationSourceType.CONTEST, CONTEST_ID,
                "Best exhaust system · Casino Square Cars & Coffee");
        verify(reputationService).award(OWNER_A, ReputationReasons.CONTEST_FIRST_PLACE, source);
        verify(reputationService).award(OWNER_B, ReputationReasons.CONTEST_SECOND_PLACE, source);
        verify(reputationService).award(OWNER_C, ReputationReasons.CONTEST_THIRD_PLACE, source);
        verify(reputationService, never()).award(eq(OWNER_D), anyString(), any(ReputationSource.class));

        verify(badgeService).awardForTrigger(OWNER_A, BadgeTrigger.CONTEST_WON, NOW);
        verify(badgeService).awardForTrigger(OWNER_A, BadgeTrigger.CONTEST_PODIUM, NOW);
        verify(badgeService).awardForTrigger(OWNER_B, BadgeTrigger.CONTEST_PODIUM, NOW);
        verify(badgeService).awardForTrigger(OWNER_C, BadgeTrigger.CONTEST_PODIUM, NOW);
        verify(badgeService, never()).awardForTrigger(eq(OWNER_B), eq(BadgeTrigger.CONTEST_WON), any());
        verify(badgeService, never()).awardForTrigger(eq(OWNER_D), any(), any());

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        ContestFinishedEvent finished = (ContestFinishedEvent) published.getValue();
        assertThat(finished.winnerCarId()).isEqualTo(CAR_A);
        assertThat(finished.podium()).extracting(ContestFinishedEvent.Placement::rank).containsExactly(1, 2, 3);
        assertThat(finished.podium().get(0).carName()).isEqualTo("BMW M4");
        // The podium owners get their own notification; the audience is everyone else at the meet.
        assertThat(finished.audienceIds()).containsExactly(SPECTATOR, OWNER_D);

        verify(boardPublisher).publishStatus(EVENT_ID, CONTEST_ID, ContestEntity.STATUS_FINISHED);
    }

    @Test
    void finalizingWithNoVotesPaysNobodyAndAnnouncesNothing() {
        ContestEntity contest = openContest();
        when(entryRepository.findByIdContestIdAndStatus(CONTEST_ID, ContestEntryEntity.ACCEPTED))
                .thenReturn(List.of(entry(CAR_A, OWNER_A, 0, null), entry(CAR_B, OWNER_B, 0, null)));

        finalizer.finalizeLocked(contest, event, null, true, NOW.plus(2, ChronoUnit.HOURS));

        assertThat(contest.getStatus()).isEqualTo(ContestEntity.STATUS_FINISHED);
        assertThat(contest.isFinishedEarly()).isFalse();
        verify(reputationService, never()).award(any(), anyString(), any(ReputationSource.class));
        verify(badgeService, never()).awardForTrigger(any(), any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void aCancelledMeetClosesItsContestsWithoutAwards() {
        ContestEntity contest = openContest();
        when(entryRepository.findByIdContestIdAndStatus(CONTEST_ID, ContestEntryEntity.ACCEPTED))
                .thenReturn(List.of(entry(CAR_A, OWNER_A, 9, NOW)));

        finalizer.finalizeLocked(contest, event, ORGANIZER, false, NOW);

        assertThat(contest.getStatus()).isEqualTo(ContestEntity.STATUS_FINISHED);
        verify(reputationService, never()).award(any(), anyString(), any(ReputationSource.class));
        verify(badgeService, never()).awardForTrigger(any(), any(), any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void finalizingIsIdempotent() {
        ContestEntity contest = openContest();
        contest.setStatus(ContestEntity.STATUS_FINISHED);
        contest.setFinishedAt(NOW.minus(1, ChronoUnit.MINUTES));

        assertThat(finalizer.finalizeLocked(contest, event, ORGANIZER, true, NOW)).isFalse();
        verify(entryRepository, never()).findByIdContestIdAndStatus(any(), any());
        verify(reputationService, never()).award(any(), anyString(), any(ReputationSource.class));
    }

    @Test
    void finishingAfterThePlannedCloseIsNotEarly() {
        ContestEntity contest = openContest();
        contest.setClosesAt(NOW.minus(1, ChronoUnit.MINUTES));
        when(entryRepository.findByIdContestIdAndStatus(CONTEST_ID, ContestEntryEntity.ACCEPTED)).thenReturn(List.of());

        assertThat(finalizer.finalizeLocked(contest, event, ORGANIZER, true, NOW)).isTrue();

        assertThat(contest.getStatus()).isEqualTo(ContestEntity.STATUS_FINISHED);
        assertThat(contest.getFinishedBy()).isEqualTo(ORGANIZER);
        assertThat(contest.isFinishedEarly()).isFalse();
    }

    // ---- opening ------------------------------------------------------------

    @Test
    void openingTellsTheMeetMinusTheActor() {
        ContestEntity contest = openContest();
        contest.setStatus(ContestEntity.STATUS_SCHEDULED);
        when(entryRepository.findByIdContestIdAndStatus(CONTEST_ID, ContestEntryEntity.ACCEPTED))
                .thenReturn(List.of(entry(CAR_B, OWNER_B, 0, null)));

        assertThat(finalizer.open(contest, OWNER_A)).isTrue();

        assertThat(contest.getStatus()).isEqualTo(ContestEntity.STATUS_OPEN);
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        ContestOpenedEvent opened = (ContestOpenedEvent) published.getValue();
        assertThat(opened.recipientIds()).containsExactly(SPECTATOR, OWNER_B);
        verify(boardPublisher).publishStatus(EVENT_ID, CONTEST_ID, ContestEntity.STATUS_OPEN);
    }
}
