package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarOwnerDto;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.mapevents.ContestEntryDecidedEvent;
import com.tweakdapp.backend.mapevents.ContestEntryRequestedEvent;
import com.tweakdapp.backend.mapevents.dto.ContestDto;
import com.tweakdapp.backend.mapevents.dto.request.CreateContestRequest;
import com.tweakdapp.backend.mapevents.dto.request.UpdateContestRequest;
import com.tweakdapp.backend.mapevents.exception.CarNotOwnedException;
import com.tweakdapp.backend.mapevents.exception.ContestClosedException;
import com.tweakdapp.backend.mapevents.exception.ContestEntryNotFoundException;
import com.tweakdapp.backend.mapevents.exception.ContestLimitException;
import com.tweakdapp.backend.mapevents.exception.ContestNotOpenException;
import com.tweakdapp.backend.mapevents.exception.InvalidContestException;
import com.tweakdapp.backend.mapevents.exception.MapEventNotFoundException;
import com.tweakdapp.backend.mapevents.exception.NotEligibleToVoteException;
import com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException;
import com.tweakdapp.backend.mapevents.internal.entities.ContestCategoryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryId;
import com.tweakdapp.backend.mapevents.internal.entities.ContestVoteEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestVoteId;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventAttendeeEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventAttendeeId;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventCategoryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventOrganizerEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventParticipantEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventParticipantId;
import com.tweakdapp.backend.mapevents.internal.realtime.ContestBoardPublisher;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestCategoryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestEntryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestVoteRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventAttendeeRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventOrganizerRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventParticipantRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRepository;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The permission and eligibility rules of contests, each asserted directly: who may create and
 * decide, the planned-window bounds, who may vote and for what, the entry request round trip, and
 * the fact that voting starts and ends only when an organizer says so.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapEventContestsServiceImplTest {

    private static final UUID ORGANIZER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID ATTENDEE = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CONTEST_ID = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    /** Someone with a car in the line-up but no RSVP row of their own. */
    private static final UUID PARTICIPANT = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID OTHER_CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private static final Instant NOW = Instant.now();

    @Mock private ContestRepository contestRepository;
    @Mock private ContestCategoryRepository categoryRepository;
    @Mock private ContestEntryRepository entryRepository;
    @Mock private ContestVoteRepository voteRepository;
    @Mock private MapEventRepository eventRepository;
    @Mock private MapEventOrganizerRepository organizerRepository;
    @Mock private MapEventAttendeeRepository attendeeRepository;
    @Mock private MapEventParticipantRepository participantRepository;
    @Mock private ProfileService profileService;
    @Mock private GarageService garageService;
    @Mock private StorageService storageService;
    @Mock private ApplicationEventPublisher events;
    @Mock private ContestFinalizer finalizer;
    @Mock private ContestBoardPublisher boardPublisher;

    private MapEventContestsServiceImpl service;
    private MapEventEntity event;
    private ContestEntity contest;

    @BeforeEach
    void setUp() {
        service = new MapEventContestsServiceImpl(contestRepository, categoryRepository, entryRepository, voteRepository,
                eventRepository, organizerRepository, attendeeRepository, participantRepository, profileService,
                garageService, storageService, events, finalizer, boardPublisher);
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        MapEventCategoryEntity category = new MapEventCategoryEntity();
        category.setId(MapEventCategoryEntity.CAR_MEET);
        category.setLabel("Car meet");
        category.setAvailable(true);

        event = new MapEventEntity();
        event.setId(EVENT_ID);
        event.setCategory(category);
        event.setTitle("Casino Square Cars & Coffee");
        event.setStatus(MapEventEntity.STATUS_LIVE);
        event.setApprovalStatus(MapEventEntity.APPROVAL_ACCEPTED);
        event.setStartsAt(NOW.minus(2, ChronoUnit.HOURS));
        event.setEndsAt(NOW.plus(3, ChronoUnit.HOURS));
        event.setCreatedBy(ORGANIZER);
        when(eventRepository.findWithCategoryById(EVENT_ID)).thenReturn(Optional.of(event));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        ContestCategoryEntity exhaust = new ContestCategoryEntity();
        exhaust.setId("exhaust");
        exhaust.setLabel("Best exhaust system");
        exhaust.setIcon("exhaust");
        exhaust.setAvailable(true);
        when(categoryRepository.findById("exhaust")).thenReturn(Optional.of(exhaust));

        contest = new ContestEntity();
        contest.setId(CONTEST_ID);
        contest.setEventId(EVENT_ID);
        contest.setCategory(exhaust);
        contest.setTitle("Best exhaust system");
        contest.setStatus(ContestEntity.STATUS_OPEN);
        contest.setOpensAt(NOW.minus(1, ChronoUnit.HOURS));
        contest.setClosesAt(NOW.plus(1, ChronoUnit.HOURS));
        contest.setCreatedBy(ORGANIZER);
        when(contestRepository.lockById(CONTEST_ID)).thenReturn(Optional.of(contest));
        when(contestRepository.findByIdAndEventId(CONTEST_ID, EVENT_ID)).thenReturn(Optional.of(contest));
        when(contestRepository.findByEventId(EVENT_ID)).thenReturn(List.of(contest));

        when(organizerRepository.existsByEventIdAndIndividualOrganizerId(any(), any())).thenReturn(false);
        when(organizerRepository.existsByEventIdAndIndividualOrganizerId(EVENT_ID, ORGANIZER)).thenReturn(true);
        MapEventOrganizerEntity creator = new MapEventOrganizerEntity();
        creator.setId(UUID.randomUUID());
        creator.setEventId(EVENT_ID);
        creator.setIndividualOrganizerId(ORGANIZER);
        creator.setRole(MapEventOrganizerEntity.ROLE_CREATOR);
        when(organizerRepository.findByEventIdOrderByRoleAscCreatedAtAsc(EVENT_ID)).thenReturn(List.of(creator));

        when(garageService.findCarOwnerIds(List.of(CAR_ID))).thenReturn(Map.of(CAR_ID, OWNER));
        when(garageService.findCarsByIds(any())).thenAnswer(inv -> ((List<UUID>) inv.getArgument(0)).stream()
                .map(id -> new CarSummaryDto(id, "BMW", "M4", 2023, 503, 650, null, null, new CarOwnerDto(OWNER, "sasha")))
                .toList());
        when(finalizer.open(any(), any())).thenAnswer(inv -> {
            ContestEntity c = inv.getArgument(0);
            if (!c.isScheduled()) return false;
            c.setStatus(ContestEntity.STATUS_OPEN);
            return true;
        });
    }

    private void attending(UUID userId) {
        MapEventAttendeeEntity rsvp = new MapEventAttendeeEntity();
        rsvp.setId(new MapEventAttendeeId(userId, EVENT_ID));
        rsvp.setStatus(MapEventAttendeeEntity.ATTENDING);
        when(attendeeRepository.findById(new MapEventAttendeeId(userId, EVENT_ID))).thenReturn(Optional.of(rsvp));
    }

    private void acceptedParticipant(UUID carId, UUID ownerId) {
        MapEventParticipantEntity row = new MapEventParticipantEntity();
        row.setId(new MapEventParticipantId(EVENT_ID, carId));
        row.setOwnerId(ownerId);
        row.setStatus(MapEventParticipantEntity.ACCEPTED);
        when(participantRepository.findById(new MapEventParticipantId(EVENT_ID, carId))).thenReturn(Optional.of(row));
    }

    /** An accepted car in the event's line-up, as the eligibility lookups see it. */
    private void inTheLineup(UUID carId, UUID ownerId) {
        MapEventParticipantEntity row = new MapEventParticipantEntity();
        row.setId(new MapEventParticipantId(EVENT_ID, carId));
        row.setOwnerId(ownerId);
        row.setStatus(MapEventParticipantEntity.ACCEPTED);
        when(participantRepository.findByIdEventIdAndOwnerIdAndStatus(
                EVENT_ID, ownerId, MapEventParticipantEntity.ACCEPTED)).thenReturn(List.of(row));
    }

    private ContestEntryEntity acceptedEntry(UUID carId, UUID ownerId) {
        ContestEntryEntity entry = new ContestEntryEntity();
        entry.setId(new ContestEntryId(CONTEST_ID, carId));
        entry.setOwnerId(ownerId);
        entry.setStatus(ContestEntryEntity.ACCEPTED);
        when(entryRepository.findById(new ContestEntryId(CONTEST_ID, carId))).thenReturn(Optional.of(entry));
        return entry;
    }

    private static CreateContestRequest create(Instant opensAt, Instant closesAt) {
        return new CreateContestRequest("exhaust", "Best exhaust system", "Note and build quality.", opensAt, closesAt);
    }

    // ---- creating -----------------------------------------------------------

    @Test
    void onlyAnOrganizerCanCreateAContest() {
        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.createContest(ATTENDEE, EVENT_ID, create(NOW, NOW.plus(1, ChronoUnit.HOURS))));
    }

    @Test
    void aContestMustCloseAfterItOpensAndWithinTwelveHoursOfTheEventEnd() {
        assertThatExceptionOfType(InvalidContestException.class)
                .isThrownBy(() -> service.createContest(ORGANIZER, EVENT_ID,
                        create(NOW.plus(1, ChronoUnit.HOURS), NOW.plus(30, ChronoUnit.MINUTES))));
        assertThatExceptionOfType(InvalidContestException.class)
                .isThrownBy(() -> service.createContest(ORGANIZER, EVENT_ID,
                        create(NOW, NOW.plus(3 + 13, ChronoUnit.HOURS))));
    }

    /**
     * The create-event wizard lets an organizer line up an event's contests in the same sitting
     * they submit the event for review, and that event is born pending. Authoring is therefore
     * allowed before approval; anything the public takes part in is not — see
     * {@link #votingCannotOpenBeforeStaffApproveTheEvent}.
     */
    @Test
    void anOrganizerCanAuthorContestsOnAnEventStillWaitingForApproval() {
        event.setApprovalStatus(MapEventEntity.APPROVAL_PENDING);
        when(contestRepository.saveAndFlush(any())).thenAnswer(inv -> {
            ContestEntity saved = inv.getArgument(0);
            when(contestRepository.findByIdAndEventId(saved.getId(), EVENT_ID)).thenReturn(Optional.of(saved));
            return saved;
        });

        ContestDto created = service.createContest(ORGANIZER, EVENT_ID,
                create(NOW.plus(1, ChronoUnit.HOURS), NOW.plus(2, ChronoUnit.HOURS)));

        assertThat(created.status()).isEqualTo(ContestEntity.STATUS_SCHEDULED);

        contest.setStatus(ContestEntity.STATUS_SCHEDULED);
        service.updateContest(ORGANIZER, EVENT_ID, CONTEST_ID,
                new UpdateContestRequest("Loudest exhaust", null, null, NOW.plus(2, ChronoUnit.HOURS)));
        assertThat(contest.getTitle()).isEqualTo("Loudest exhaust");
    }

    @Test
    void votingCannotOpenBeforeStaffApproveTheEvent() {
        event.setApprovalStatus(MapEventEntity.APPROVAL_PENDING);
        contest.setStatus(ContestEntity.STATUS_SCHEDULED);

        assertThatExceptionOfType(ContestClosedException.class)
                .isThrownBy(() -> service.openContest(ORGANIZER, EVENT_ID, CONTEST_ID));
    }

    @Test
    void anEventIsCappedAtTwentyContests() {
        when(contestRepository.countByEventIdAndStatusNot(EVENT_ID, ContestEntity.STATUS_CANCELED)).thenReturn(20L);
        assertThatExceptionOfType(ContestLimitException.class)
                .isThrownBy(() -> service.createContest(ORGANIZER, EVENT_ID, create(NOW, NOW.plus(1, ChronoUnit.HOURS))));
    }

    @Test
    void aNewContestIsAlwaysScheduledEvenWhenItsPlannedWindowHasAlreadyStarted() {
        when(contestRepository.saveAndFlush(any())).thenAnswer(inv -> {
            ContestEntity saved = inv.getArgument(0);
            when(contestRepository.findByIdAndEventId(saved.getId(), EVENT_ID)).thenReturn(Optional.of(saved));
            return saved;
        });

        ContestDto later = service.createContest(ORGANIZER, EVENT_ID,
                create(NOW.plus(1, ChronoUnit.HOURS), NOW.plus(2, ChronoUnit.HOURS)));
        assertThat(later.status()).isEqualTo(ContestEntity.STATUS_SCHEDULED);

        ContestDto alreadyDue = service.createContest(ORGANIZER, EVENT_ID,
                create(NOW.minus(1, ChronoUnit.MINUTES), NOW.plus(2, ChronoUnit.HOURS)));
        assertThat(alreadyDue.status()).isEqualTo(ContestEntity.STATUS_SCHEDULED);
        assertThat(alreadyDue.opensAt()).isEqualTo(NOW.minus(1, ChronoUnit.MINUTES));

        verify(finalizer, never()).open(any(), any());
    }

    // ---- editing / finishing / deleting -------------------------------------

    @Test
    void onceOpenOnlyTheClosingTimeAndTheNoteMayChange() {
        assertThatExceptionOfType(ContestClosedException.class)
                .isThrownBy(() -> service.updateContest(ORGANIZER, EVENT_ID, CONTEST_ID,
                        new UpdateContestRequest("New title", null, null, null)));

        service.updateContest(ORGANIZER, EVENT_ID, CONTEST_ID,
                new UpdateContestRequest(null, "Louder is better.", null, NOW.plus(2, ChronoUnit.HOURS)));
        assertThat(contest.getClosesAt()).isEqualTo(NOW.plus(2, ChronoUnit.HOURS));
        assertThat(contest.getCriteria()).isEqualTo("Louder is better.");
        verify(boardPublisher).publishStatus(EVENT_ID, CONTEST_ID, ContestEntity.STATUS_OPEN);
    }

    @Test
    void movingThePlannedWindowNeverOpensAContest() {
        contest.setStatus(ContestEntity.STATUS_SCHEDULED);
        contest.setOpensAt(NOW.plus(1, ChronoUnit.HOURS));

        service.updateContest(ORGANIZER, EVENT_ID, CONTEST_ID,
                new UpdateContestRequest(null, null, NOW.minus(5, ChronoUnit.MINUTES), null));

        assertThat(contest.getOpensAt()).isEqualTo(NOW.minus(5, ChronoUnit.MINUTES));
        assertThat(contest.getStatus()).isEqualTo(ContestEntity.STATUS_SCHEDULED);
        verify(finalizer, never()).open(any(), any());
    }

    @Test
    void onlyTheOpenEndpointStartsVotingAndItIsIdempotent() {
        contest.setStatus(ContestEntity.STATUS_SCHEDULED);

        ContestDto opened = service.openContest(ORGANIZER, EVENT_ID, CONTEST_ID);
        assertThat(opened.status()).isEqualTo(ContestEntity.STATUS_OPEN);
        verify(finalizer).open(contest, ORGANIZER);

        // Second tap: still open, nothing done twice.
        assertThat(service.openContest(ORGANIZER, EVENT_ID, CONTEST_ID).status())
                .isEqualTo(ContestEntity.STATUS_OPEN);

        contest.setStatus(ContestEntity.STATUS_FINISHED);
        contest.setFinishedAt(NOW);
        assertThatExceptionOfType(ContestClosedException.class)
                .isThrownBy(() -> service.openContest(ORGANIZER, EVENT_ID, CONTEST_ID));
    }

    @Test
    void onlyAnOrganizerCanOpenVoting() {
        contest.setStatus(ContestEntity.STATUS_SCHEDULED);
        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.openContest(ATTENDEE, EVENT_ID, CONTEST_ID));
    }

    @Test
    void aFinishedContestIsReadOnly() {
        contest.setStatus(ContestEntity.STATUS_FINISHED);
        contest.setFinishedAt(NOW);
        assertThatExceptionOfType(ContestClosedException.class)
                .isThrownBy(() -> service.updateContest(ORGANIZER, EVENT_ID, CONTEST_ID,
                        new UpdateContestRequest(null, "x", null, null)));
        assertThatExceptionOfType(ContestClosedException.class)
                .isThrownBy(() -> service.decideEntry(ORGANIZER, EVENT_ID, CONTEST_ID, CAR_ID, "accepted", null));
    }

    @Test
    void finishingNeedsAnOpenContestAndDeletingNeedsAScheduledOne() {
        contest.setStatus(ContestEntity.STATUS_SCHEDULED);
        assertThatExceptionOfType(ContestNotOpenException.class)
                .isThrownBy(() -> service.finishContest(ORGANIZER, EVENT_ID, CONTEST_ID));

        contest.setStatus(ContestEntity.STATUS_OPEN);
        assertThatExceptionOfType(ContestClosedException.class)
                .isThrownBy(() -> service.deleteContest(ORGANIZER, EVENT_ID, CONTEST_ID));

        service.finishContest(ORGANIZER, EVENT_ID, CONTEST_ID);
        verify(finalizer).finalizeLocked(eq(contest), eq(event), eq(ORGANIZER), eq(true), any());
    }

    // ---- entries ------------------------------------------------------------

    @Test
    void onlyTheOwnerOfAnAcceptedEventCarCanAskToEnter() {
        acceptedParticipant(CAR_ID, OWNER);
        assertThatExceptionOfType(CarNotOwnedException.class)
                .isThrownBy(() -> service.requestEntry(ATTENDEE, EVENT_ID, CONTEST_ID, CAR_ID));

        when(participantRepository.findById(new MapEventParticipantId(EVENT_ID, CAR_ID))).thenReturn(Optional.empty());
        assertThatExceptionOfType(InvalidContestException.class)
                .isThrownBy(() -> service.requestEntry(OWNER, EVENT_ID, CONTEST_ID, CAR_ID));
    }

    @Test
    void anEntryRequestIsPendingAndTellsTheOrganizers() {
        acceptedParticipant(CAR_ID, OWNER);

        service.requestEntry(OWNER, EVENT_ID, CONTEST_ID, CAR_ID);

        ArgumentCaptor<ContestEntryEntity> saved = ArgumentCaptor.forClass(ContestEntryEntity.class);
        verify(entryRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ContestEntryEntity.PENDING);
        assertThat(saved.getValue().getOwnerId()).isEqualTo(OWNER);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(((ContestEntryRequestedEvent) published.getValue()).recipientIds()).containsExactly(ORGANIZER);
    }

    @Test
    void reSendingALiveEntryIsANoOp() {
        acceptedParticipant(CAR_ID, OWNER);
        acceptedEntry(CAR_ID, OWNER);

        service.requestEntry(OWNER, EVENT_ID, CONTEST_ID, CAR_ID);

        verify(events, never()).publishEvent(any());
    }

    @Test
    void anAcceptedEntryCannotLeaveOnceVotingIsOpen() {
        acceptedEntry(CAR_ID, OWNER);
        assertThatExceptionOfType(ContestClosedException.class)
                .isThrownBy(() -> service.withdrawEntry(OWNER, EVENT_ID, CONTEST_ID, CAR_ID));

        contest.setStatus(ContestEntity.STATUS_SCHEDULED);
        service.withdrawEntry(OWNER, EVENT_ID, CONTEST_ID, CAR_ID);
        verify(entryRepository).save(any());
    }

    @Test
    void rejectingNeedsAReasonAndAcceptingIsCappedAtForty() {
        ContestEntryEntity entry = acceptedEntry(CAR_ID, OWNER);
        entry.setStatus(ContestEntryEntity.PENDING);

        assertThatExceptionOfType(InvalidContestException.class)
                .isThrownBy(() -> service.decideEntry(ORGANIZER, EVENT_ID, CONTEST_ID, CAR_ID, "rejected", " "));

        when(entryRepository.countByIdContestIdAndStatus(CONTEST_ID, ContestEntryEntity.ACCEPTED)).thenReturn(40L);
        assertThatExceptionOfType(ContestLimitException.class)
                .isThrownBy(() -> service.decideEntry(ORGANIZER, EVENT_ID, CONTEST_ID, CAR_ID, "accepted", null));
    }

    @Test
    void rejectingAnAcceptedCarMidVoteGivesItsVotersTheirVoteBack() {
        acceptedEntry(CAR_ID, OWNER);

        service.decideEntry(ORGANIZER, EVENT_ID, CONTEST_ID, CAR_ID, "rejected", "Not a real exhaust");

        verify(voteRepository).deleteByContestIdAndCarId(CONTEST_ID, CAR_ID);
        verify(boardPublisher).markDirty(EVENT_ID, CONTEST_ID);
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        ContestEntryDecidedEvent decided = (ContestEntryDecidedEvent) published.getValue();
        assertThat(decided.accepted()).isFalse();
        assertThat(decided.reason()).isEqualTo("Not a real exhaust");
        assertThat(decided.recipientId()).isEqualTo(OWNER);
    }

    // ---- voting -------------------------------------------------------------

    @Test
    void votingNeedsAnAttendingRsvpOrACarInTheLineup() {
        acceptedEntry(CAR_ID, OWNER);
        assertThatExceptionOfType(NotEligibleToVoteException.class)
                .isThrownBy(() -> service.vote(ATTENDEE, EVENT_ID, CONTEST_ID, CAR_ID));
    }

    @Test
    void bringingACarIsEnoughToVoteWithoutASeparateRsvp() {
        acceptedEntry(CAR_ID, OWNER);
        inTheLineup(OTHER_CAR_ID, PARTICIPANT);
        contest.setStatus(ContestEntity.STATUS_OPEN);

        service.vote(PARTICIPANT, EVENT_ID, CONTEST_ID, CAR_ID);

        verify(voteRepository).save(any());
    }

    @Test
    void youCannotVoteForYourOwnCar() {
        attending(OWNER);
        acceptedEntry(CAR_ID, OWNER);
        assertThatExceptionOfType(NotEligibleToVoteException.class)
                .isThrownBy(() -> service.vote(OWNER, EVENT_ID, CONTEST_ID, CAR_ID));
    }

    @Test
    void youCanOnlyVoteForACarOnTheBallot() {
        attending(ATTENDEE);
        assertThatExceptionOfType(ContestEntryNotFoundException.class)
                .isThrownBy(() -> service.vote(ATTENDEE, EVENT_ID, CONTEST_ID, CAR_ID));
    }

    @Test
    void aScheduledContestTakesNoVotesEvenPastItsPlannedOpeningTime() {
        attending(ATTENDEE);
        acceptedEntry(CAR_ID, OWNER);

        contest.setStatus(ContestEntity.STATUS_SCHEDULED);
        contest.setOpensAt(NOW.plus(10, ChronoUnit.MINUTES));
        assertThatExceptionOfType(ContestNotOpenException.class)
                .isThrownBy(() -> service.vote(ATTENDEE, EVENT_ID, CONTEST_ID, CAR_ID));

        // Past the planned opening and still nobody opened it: it is still shut.
        contest.setOpensAt(NOW.minus(1, ChronoUnit.MINUTES));
        assertThatExceptionOfType(ContestNotOpenException.class)
                .isThrownBy(() -> service.vote(ATTENDEE, EVENT_ID, CONTEST_ID, CAR_ID));
        verify(finalizer, never()).open(any(), any());
    }

    @Test
    void anOpenContestKeepsTakingVotesPastItsPlannedClosingTime() {
        attending(ATTENDEE);
        acceptedEntry(CAR_ID, OWNER);
        contest.setStatus(ContestEntity.STATUS_OPEN);
        contest.setOpensAt(NOW.minus(2, ChronoUnit.HOURS));
        contest.setClosesAt(NOW.minus(1, ChronoUnit.MINUTES));

        service.vote(ATTENDEE, EVENT_ID, CONTEST_ID, CAR_ID);

        verify(voteRepository).save(any());
        verify(boardPublisher).markDirty(EVENT_ID, CONTEST_ID);
    }

    @Test
    void aFinishedEventClosesVoting() {
        attending(ATTENDEE);
        acceptedEntry(CAR_ID, OWNER);
        contest.setStatus(ContestEntity.STATUS_OPEN);
        event.setStatus(MapEventEntity.STATUS_PREVIOUS);

        assertThatExceptionOfType(ContestClosedException.class)
                .isThrownBy(() -> service.vote(ATTENDEE, EVENT_ID, CONTEST_ID, CAR_ID));
    }

    @Test
    void aVoteIsUpsertedAndVotingForTheSameCarAgainDoesNothing() {
        attending(ATTENDEE);
        acceptedEntry(CAR_ID, OWNER);
        ContestVoteEntity existing = new ContestVoteEntity();
        existing.setId(new ContestVoteId(CONTEST_ID, ATTENDEE));
        existing.setCarId(CAR_ID);
        when(voteRepository.findById(new ContestVoteId(CONTEST_ID, ATTENDEE))).thenReturn(Optional.of(existing));
        when(voteRepository.findByIdContestIdInAndIdVoterId(any(), eq(ATTENDEE))).thenReturn(List.of(existing));

        ContestDto dto = service.vote(ATTENDEE, EVENT_ID, CONTEST_ID, CAR_ID);

        verify(voteRepository, never()).save(any());
        verify(boardPublisher, never()).markDirty(any(), any());
        assertThat(dto.viewer().voteCarId()).isEqualTo(CAR_ID);
    }

    // ---- reads --------------------------------------------------------------

    @Test
    void anUnapprovedEventHidesItsContestsFromEveryoneButOrganizers() {
        event.setApprovalStatus(MapEventEntity.APPROVAL_PENDING);
        assertThatExceptionOfType(MapEventNotFoundException.class)
                .isThrownBy(() -> service.listContests(ATTENDEE, EVENT_ID));
        assertThat(service.listContests(ORGANIZER, EVENT_ID)).hasSize(1);
    }

    @Test
    void pendingEntriesAreOrganizerOnlyAndViewerStateIsResolvedServerSide() {
        ContestEntryEntity pending = new ContestEntryEntity();
        pending.setId(new ContestEntryId(CONTEST_ID, CAR_ID));
        pending.setOwnerId(OWNER);
        pending.setStatus(ContestEntryEntity.PENDING);
        when(entryRepository.findByIdContestIdIn(any())).thenReturn(List.of(pending));
        attending(ATTENDEE);

        ContestDto forOrganizer = service.listContests(ORGANIZER, EVENT_ID).get(0);
        assertThat(forOrganizer.pendingEntries()).hasSize(1);
        assertThat(forOrganizer.viewer().isOrganizer()).isTrue();
        assertThat(forOrganizer.viewer().canVote()).isFalse();

        ContestDto forAttendee = service.listContests(ATTENDEE, EVENT_ID).get(0);
        assertThat(forAttendee.pendingEntries()).isEmpty();
        assertThat(forAttendee.viewer().canVote()).isTrue();
        assertThat(forAttendee.entries()).isEmpty();

        inTheLineup(OTHER_CAR_ID, PARTICIPANT);
        ContestDto forParticipant = service.listContests(PARTICIPANT, EVENT_ID).get(0);
        assertThat(forParticipant.viewer().canVote()).isTrue();

        ContestDto forOwner = service.listContests(OWNER, EVENT_ID).get(0);
        assertThat(forOwner.viewer().myEntries()).singleElement()
                .satisfies(e -> assertThat(e.status()).isEqualTo(ContestEntryEntity.PENDING));
    }
}
