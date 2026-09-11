package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarOwnerDto;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.mapevents.dto.ParticipantCardDto;
import com.tweakdapp.backend.mapevents.dto.ParticipantCardKey;
import com.tweakdapp.backend.mapevents.internal.entities.ContestCategoryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryId;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventEntity;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Participant cards are derived on read, never stored, so these pin down exactly when an
 * {@code (event, car)} pair is a card and what it says: only for an approved event an organizer has
 * marked finished — the clock alone does not count — and only for an accepted car, with a place
 * named only inside the podium with at least one vote.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParticipantCardsTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000d9");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID PAINT = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID LOUDEST = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final UUID INTERIOR = UUID.fromString("00000000-0000-0000-0000-0000000000f3");

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
    private MapEventParticipantEntity participant;
    private final List<ContestEntity> finishedContests = new ArrayList<>();
    private final List<ContestEntryEntity> entries = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new MapEventContestsServiceImpl(contestRepository, categoryRepository, entryRepository, voteRepository,
                eventRepository, organizerRepository, attendeeRepository, participantRepository, profileService,
                garageService, storageService, events, finalizer, boardPublisher);

        event = new MapEventEntity();
        event.setId(EVENT_ID);
        event.setTitle("Casino Square Cars & Coffee");
        event.setStatus(MapEventEntity.STATUS_PREVIOUS);
        event.setApprovalStatus(MapEventEntity.APPROVAL_ACCEPTED);
        ReflectionTestUtils.setField(event, "attendeesCount", 247);
        when(eventRepository.findAllById(any())).thenAnswer(inv -> List.of(event));

        participant = new MapEventParticipantEntity();
        participant.setId(new MapEventParticipantId(EVENT_ID, CAR_ID));
        participant.setOwnerId(OWNER);
        participant.setStatus(MapEventParticipantEntity.ACCEPTED);
        when(participantRepository.findAllById(any())).thenAnswer(inv -> List.of(participant));
        when(participantRepository.findById(new MapEventParticipantId(EVENT_ID, CAR_ID)))
                .thenAnswer(inv -> Optional.of(participant));
        when(participantRepository.findByIdEventIdAndOwnerIdAndStatus(EVENT_ID, OWNER, MapEventParticipantEntity.ACCEPTED))
                .thenAnswer(inv -> List.of(participant));

        when(contestRepository.findByEventIdInAndStatus(any(), eq(ContestEntity.STATUS_FINISHED)))
                .thenAnswer(inv -> finishedContests);
        when(entryRepository.findByIdContestIdIn(any())).thenAnswer(inv -> entries);
        when(garageService.findCarsByIds(any())).thenReturn(List.of(new CarSummaryDto(CAR_ID, "BMW", "M4 Competition",
                2023, 503, 650, null, null, new CarOwnerDto(OWNER, "torque_sasha"))));
    }

    @Test
    void aCardExistsOnlyOnceAnOrganizerMarksTheEventFinished() {
        // Past its end time but never marked finished: still no card — the owner's rule.
        event.setStatus(MapEventEntity.STATUS_LIVE);
        event.setEndsAt(Instant.now().minus(3, ChronoUnit.HOURS));

        assertThat(service.listMyParticipantCards(OWNER, EVENT_ID)).isEmpty();
    }

    @Test
    void aCancelledOrUnapprovedEventHasNoCards() {
        event.setStatus(MapEventEntity.STATUS_CANCELED);
        assertThat(service.listMyParticipantCards(OWNER, EVENT_ID)).isEmpty();

        event.setStatus(MapEventEntity.STATUS_PREVIOUS);
        event.setApprovalStatus(MapEventEntity.APPROVAL_PENDING);
        assertThat(service.listMyParticipantCards(OWNER, EVENT_ID)).isEmpty();
    }

    @Test
    void aParticipantWhoEnteredNoContestStillGetsACard() {
        List<ParticipantCardDto> cards = service.listMyParticipantCards(OWNER, EVENT_ID);

        assertThat(cards).singleElement().satisfies(card -> {
            assertThat(card.eventTitle()).isEqualTo("Casino Square Cars & Coffee");
            assertThat(card.eventAttendeesCount()).isEqualTo(247);
            assertThat(card.car().id()).isEqualTo(CAR_ID);
            assertThat(card.bestRank()).isNull();
            assertThat(card.contests()).isEmpty();
        });
    }

    @Test
    void placesAreNamedOnlyInsideThePodiumWithAVote() {
        finishedContests.addAll(List.of(
                contest(PAINT, "Best paint / wrap"), contest(LOUDEST, "Loudest"), contest(INTERIOR, "Best interior")));
        entries.addAll(List.of(
                entry(PAINT, 1, 12),      // on the podium
                entry(LOUDEST, 5, 3),     // entered, placed nowhere
                entry(INTERIOR, 2, 0)));  // "2nd" with no votes is no place at all

        ParticipantCardDto card = service.listMyParticipantCards(OWNER, EVENT_ID).getFirst();

        assertThat(card.bestRank()).isEqualTo(1);
        // Podium first, then the rest by title — stable, so the card's one line never reshuffles.
        assertThat(card.contests()).extracting(c -> c.title(), c -> c.finalRank()).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Best paint / wrap", 1),
                org.assertj.core.groups.Tuple.tuple("Best interior", null),
                org.assertj.core.groups.Tuple.tuple("Loudest", null));
    }

    @Test
    void aCarThatIsNotAnAcceptedParticipantHasNoCard() {
        participant.setStatus(MapEventParticipantEntity.REJECTED);

        assertThat(service.findParticipantCards(List.of(new ParticipantCardKey(EVENT_ID, CAR_ID)))).isEmpty();
        assertThat(service.findOwnedParticipantCard(OWNER, EVENT_ID, CAR_ID)).isEmpty();
    }

    @Test
    void onlyTheCarsOwnerCanClaimItsCard() {
        assertThat(service.findOwnedParticipantCard(STRANGER, EVENT_ID, CAR_ID)).isEmpty();
        assertThat(service.findOwnedParticipantCard(OWNER, EVENT_ID, CAR_ID)).isPresent();
    }

    @Test
    void aCarThatNoLongerResolvesHasNoCardRatherThanABrokenOne() {
        when(garageService.findCarsByIds(any())).thenReturn(List.of());

        assertThat(service.listMyParticipantCards(OWNER, EVENT_ID)).isEmpty();
    }

    private static ContestEntity contest(UUID id, String title) {
        ContestCategoryEntity category = new ContestCategoryEntity();
        category.setId("custom");
        category.setLabel(title);
        category.setIcon("trophy");
        ContestEntity contest = new ContestEntity();
        contest.setId(id);
        contest.setEventId(EVENT_ID);
        contest.setTitle(title);
        contest.setStatus(ContestEntity.STATUS_FINISHED);
        contest.setCategory(category);
        return contest;
    }

    private static ContestEntryEntity entry(UUID contestId, int finalRank, int votes) {
        ContestEntryEntity entry = new ContestEntryEntity();
        entry.setId(new ContestEntryId(contestId, CAR_ID));
        entry.setOwnerId(OWNER);
        entry.setStatus("accepted");
        entry.setFinalRank((short) finalRank);
        entry.setVotesCount(votes);
        return entry;
    }
}
