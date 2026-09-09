package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.business.BusinessService;
import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.mapevents.MapEventWithdrawalDecidedEvent;
import com.tweakdapp.backend.mapevents.MapEventWithdrawalRequestedEvent;
import com.tweakdapp.backend.mapevents.dto.MapEventDto;
import com.tweakdapp.backend.mapevents.dto.MapEventParticipantDto;
import com.tweakdapp.backend.mapevents.dto.MapEventWithdrawalRequestDto;
import com.tweakdapp.backend.mapevents.dto.request.AddOrganizerRequest;
import com.tweakdapp.backend.mapevents.dto.request.CreateMapEventRequest;
import com.tweakdapp.backend.mapevents.dto.request.UpdateMapEventRequest;
import com.tweakdapp.backend.mapevents.exception.CarNotOwnedException;
import com.tweakdapp.backend.mapevents.exception.EventClosedException;
import com.tweakdapp.backend.mapevents.exception.EventNotEditableException;
import com.tweakdapp.backend.mapevents.exception.InvalidMapEventException;
import com.tweakdapp.backend.mapevents.exception.InvalidSearchAreaException;
import com.tweakdapp.backend.mapevents.exception.MapEventNotFoundException;
import com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException;
import com.tweakdapp.backend.mapevents.internal.entities.CarMeetEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventCategoryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventOrganizerEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventParticipantEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventParticipantId;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventRuleEntity;
import com.tweakdapp.backend.mapevents.internal.repositories.CarMeetRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestEntryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventAttendeeRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventCategoryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventOrganizerRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventParticipantRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRuleRepository;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.shared.geo.GeoSupport;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-unit behaviour of {@link MapEventsServiceImpl} with every collaborator mocked: the
 * permission rules (organizer vs creator vs stranger), the approval lock, the visibility rule that
 * hides unapproved events behind a 404, the RSVP and registration windows, and the cover-key
 * ownership check.
 *
 * <p>These are the rules that decide who can change what, so each is asserted directly rather than
 * inferred from a happy path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MapEventsServiceImplTest {

    private static final UUID CREATOR = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID CO_ORGANIZER = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CAR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    @Mock private MapEventRepository eventRepository;
    @Mock private MapEventCategoryRepository categoryRepository;
    @Mock private CarMeetRepository carMeetRepository;
    @Mock private MapEventOrganizerRepository organizerRepository;
    @Mock private MapEventAttendeeRepository attendeeRepository;
    @Mock private MapEventParticipantRepository participantRepository;
    @Mock private MapEventRuleRepository ruleRepository;
    @Mock private ProfileService profileService;
    @Mock private GarageService garageService;
    @Mock private BusinessService businessService;
    @Mock private StorageService storageService;
    @Mock private ApplicationEventPublisher events;
    @Mock private MapboxGeocodingClient mapboxGeocodingClient;
    @Mock private ContestFinalizer contestFinalizer;
    @Mock private ContestEntryRepository contestEntryRepository;

    private MapEventsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MapEventsServiceImpl(
                eventRepository, categoryRepository, carMeetRepository, organizerRepository,
                attendeeRepository, participantRepository, ruleRepository, profileService, garageService,
                businessService, storageService, events, mapboxGeocodingClient, contestFinalizer,
                contestEntryRepository);
        // @PersistenceContext is field-injected, so a pure unit test has to supply it by hand.
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        // createEvent reassigns `event` from the merge result, so hand the same entity back.
        when(eventRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        // Only the creator and the co-organizer organize this event.
        when(organizerRepository.existsByEventIdAndIndividualOrganizerId(any(), any())).thenReturn(false);
        when(organizerRepository.existsByEventIdAndIndividualOrganizerId(EVENT_ID, CREATOR)).thenReturn(true);
        when(organizerRepository.existsByEventIdAndIndividualOrganizerId(EVENT_ID, CO_ORGANIZER)).thenReturn(true);
        when(organizerRepository.findByEventIdOrderByRoleAscCreatedAtAsc(EVENT_ID))
                .thenReturn(List.of(organizerRow(CREATOR, MapEventOrganizerEntity.ROLE_CREATOR)));
        when(participantRepository.findByIdEventIdAndOwnerId(any(), any())).thenReturn(List.of());
        when(attendeeRepository.findById(any())).thenReturn(Optional.empty());
        when(profileService.findByIds(any())).thenReturn(List.of());
        when(ruleRepository.findByEventIdOrderBySortOrderAsc(any())).thenReturn(List.of());
    }

    // ---- fixtures -----------------------------------------------------------

    private static MapEventCategoryEntity carMeetCategory() {
        MapEventCategoryEntity category = new MapEventCategoryEntity();
        category.setId(MapEventCategoryEntity.CAR_MEET);
        category.setLabel("Car meet");
        category.setAvailable(true);
        return category;
    }

    private static MapEventOrganizerEntity organizerRow(UUID userId, String role) {
        MapEventOrganizerEntity row = new MapEventOrganizerEntity();
        row.setId(UUID.randomUUID());
        row.setEventId(EVENT_ID);
        row.setIndividualOrganizerId(userId);
        row.setRole(role);
        return row;
    }

    /** An approved, upcoming event created by {@link #CREATOR}. */
    private MapEventEntity event(String approvalStatus, String status, Instant startsAt, Instant endsAt) {
        MapEventEntity event = new MapEventEntity();
        event.setId(EVENT_ID);
        event.setCategory(carMeetCategory());
        event.setTitle("Sunday meet");
        event.setDescription("Come along");
        event.setLocationName("Iulius Mall");
        event.setStartsAt(startsAt);
        event.setEndsAt(endsAt);
        event.setLocation(GeoSupport.point(46.7712, 23.6236));
        event.setStatus(status);
        event.setApprovalStatus(approvalStatus);
        event.setCreatedBy(CREATOR);
        event.setRequiresParticipantApproval(true);
        return event;
    }

    private MapEventEntity approvedUpcoming() {
        return event(MapEventEntity.APPROVAL_ACCEPTED, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null);
    }

    private void existing(MapEventEntity event) {
        when(eventRepository.findWithCategoryById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    private static MapEventParticipantEntity participant(UUID ownerId, UUID carId, String status) {
        MapEventParticipantEntity row = new MapEventParticipantEntity();
        row.setId(new MapEventParticipantId(EVENT_ID, carId));
        row.setOwnerId(ownerId);
        row.setStatus(status);
        return row;
    }

    private static CarSummaryDto carSummary(UUID carId) {
        return new CarSummaryDto(carId, "Brand", "Model", 2021, 300, 400, null, null, null);
    }

    // ---- visibility ---------------------------------------------------------

    @Test
    void anUnapprovedEventIsInvisibleToNonOrganizers() {
        existing(event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null));

        // The same 404 as a nonexistent id, so submissions cannot be found by probing.
        assertThatExceptionOfType(MapEventNotFoundException.class)
                .isThrownBy(() -> service.getEvent(STRANGER, EVENT_ID));
    }

    @Test
    void anUnapprovedEventIsVisibleToItsOrganizers() {
        existing(event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null));

        MapEventDto dto = service.getEvent(CREATOR, EVENT_ID);

        assertThat(dto.approvalStatus()).isEqualTo(MapEventEntity.APPROVAL_PENDING);
        assertThat(dto.viewer().isCreator()).isTrue();
        assertThat(dto.viewer().isOrganizer()).isTrue();
        assertThat(dto.viewer().canEdit()).isTrue();
    }

    @Test
    void theRejectionReasonIsOnlyEverShownToOrganizers() {
        MapEventEntity rejected = event(MapEventEntity.APPROVAL_REJECTED, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null);
        rejected.setRejectionReason("Location is not specific enough");
        existing(rejected);

        assertThat(service.getEvent(CREATOR, EVENT_ID).rejectionReason())
                .isEqualTo("Location is not specific enough");
        // A rejected event is not approved, so a stranger cannot even load it — the reason is
        // stripped for any non-organizer that does reach assembly (e.g. the admin read path).
        assertThatExceptionOfType(MapEventNotFoundException.class)
                .isThrownBy(() -> service.getEvent(STRANGER, EVENT_ID));
    }

    // ---- the approval lock --------------------------------------------------

    @Test
    void anApprovedEventCanNoLongerBeEdited() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(EventNotEditableException.class)
                .isThrownBy(() -> service.updateEvent(CREATOR, EVENT_ID,
                        new UpdateMapEventRequest("New title", null, null, null, null, null, null, null, null, null)));
    }

    @Test
    void aPendingEventCanBeEditedByAnyOrganizer() {
        MapEventEntity pending = event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null);
        existing(pending);

        service.updateEvent(CO_ORGANIZER, EVENT_ID,
                new UpdateMapEventRequest("New title", null, null, null, null, null, null, null, null, null));

        assertThat(pending.getTitle()).isEqualTo("New title");
    }

    @Test
    void editingARejectedEventResubmitsItAndClearsTheReason() {
        MapEventEntity rejected = event(MapEventEntity.APPROVAL_REJECTED, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null);
        rejected.setRejectionReason("Location is not specific enough");
        existing(rejected);

        service.updateEvent(CREATOR, EVENT_ID,
                new UpdateMapEventRequest(null, null, "Central car park", null, null, null, null, null, null, null));

        assertThat(rejected.getApprovalStatus()).isEqualTo(MapEventEntity.APPROVAL_PENDING);
        assertThat(rejected.getRejectionReason()).isNull();
    }

    @Test
    void aStrangerCannotEditAnEvent() {
        existing(event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null));

        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.updateEvent(STRANGER, EVENT_ID,
                        new UpdateMapEventRequest("Hijacked", null, null, null, null, null, null, null, null, null)));
    }

    // ---- creator-only powers ------------------------------------------------

    @Test
    void onlyTheCreatorCanDeleteAnEvent() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.deleteEvent(CO_ORGANIZER, EVENT_ID));

        service.deleteEvent(CREATOR, EVENT_ID);
        verify(eventRepository).delete(any());
    }

    @Test
    void onlyTheCreatorCanAddOrganizers() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.addOrganizer(CO_ORGANIZER, EVENT_ID,
                        new AddOrganizerRequest(STRANGER, null)));
    }

    @Test
    void anOrganizerRequestMustNameExactlyOneOfAUserAndABusiness() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.addOrganizer(CREATOR, EVENT_ID,
                        new AddOrganizerRequest(null, null)));
        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.addOrganizer(CREATOR, EVENT_ID,
                        new AddOrganizerRequest(STRANGER, UUID.randomUUID())));
    }

    @Test
    void theCreatorCannotBeRemovedAsOrganizer() {
        existing(approvedUpcoming());
        MapEventOrganizerEntity creatorRow = organizerRow(CREATOR, MapEventOrganizerEntity.ROLE_CREATOR);
        when(organizerRepository.findByEventIdAndId(EVENT_ID, creatorRow.getId()))
                .thenReturn(Optional.of(creatorRow));

        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.removeOrganizer(CREATOR, EVENT_ID, creatorRow.getId()));
        verify(organizerRepository, never()).delete(any());
    }

    // ---- RSVP window --------------------------------------------------------

    @Test
    void rsvpIsOpenWhileTheEventIsStillRunning() {
        existing(event(MapEventEntity.APPROVAL_ACCEPTED, MapEventEntity.STATUS_LIVE,
                Instant.now().minus(1, ChronoUnit.HOURS), Instant.now().plus(3, ChronoUnit.HOURS)));

        service.setAttendance(STRANGER, EVENT_ID, "attending");

        verify(attendeeRepository).save(any());
    }

    @Test
    void rsvpClosesOnceTheEventHasEnded() {
        existing(event(MapEventEntity.APPROVAL_ACCEPTED, MapEventEntity.STATUS_LIVE,
                Instant.now().minus(5, ChronoUnit.HOURS), Instant.now().minus(1, ChronoUnit.HOURS)));

        assertThatExceptionOfType(EventClosedException.class)
                .isThrownBy(() -> service.setAttendance(STRANGER, EVENT_ID, "attending"));
    }

    @Test
    void rsvpClosesOnceAnOrganizerMarksTheEventFinished() {
        existing(event(MapEventEntity.APPROVAL_ACCEPTED, MapEventEntity.STATUS_PREVIOUS,
                Instant.now().minus(5, ChronoUnit.HOURS), null));

        assertThatExceptionOfType(EventClosedException.class)
                .isThrownBy(() -> service.setAttendance(STRANGER, EVENT_ID, "attending"));
    }

    @Test
    void rsvpIsClosedOnACancelledEvent() {
        existing(event(MapEventEntity.APPROVAL_ACCEPTED, MapEventEntity.STATUS_CANCELED,
                Instant.now().plus(2, ChronoUnit.DAYS), null));

        assertThatExceptionOfType(EventClosedException.class)
                .isThrownBy(() -> service.setAttendance(STRANGER, EVENT_ID, "attending"));
    }

    @Test
    void anUnrecognisedRsvpValueIsRejected() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.setAttendance(STRANGER, EVENT_ID, "maybe"));
    }

    // ---- car registration ---------------------------------------------------

    @Test
    void onlyTheCarsOwnerMayEnterIt() {
        existing(approvedUpcoming());
        when(garageService.findCarOwnerIds(List.of(CAR_ID))).thenReturn(Map.of(CAR_ID, CREATOR));

        assertThatExceptionOfType(CarNotOwnedException.class)
                .isThrownBy(() -> service.registerCar(STRANGER, EVENT_ID, CAR_ID));
    }

    @Test
    void registrationClosesAtTheCategoryDeadline() {
        existing(approvedUpcoming());
        when(garageService.findCarOwnerIds(List.of(CAR_ID))).thenReturn(Map.of(CAR_ID, STRANGER));

        CarMeetEntity meet = new CarMeetEntity();
        meet.setEventId(EVENT_ID);
        meet.setRegistrationDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        when(carMeetRepository.findById(EVENT_ID)).thenReturn(Optional.of(meet));

        assertThatExceptionOfType(EventClosedException.class)
                .isThrownBy(() -> service.registerCar(STRANGER, EVENT_ID, CAR_ID));
    }

    @Test
    void aCarStartsPendingWhenTheEventVetsItsLineUpAndAcceptedOtherwise() {
        MapEventEntity vets = approvedUpcoming();
        existing(vets);
        when(garageService.findCarOwnerIds(List.of(CAR_ID))).thenReturn(Map.of(CAR_ID, STRANGER));
        when(participantRepository.findById(any())).thenReturn(Optional.empty());

        service.registerCar(STRANGER, EVENT_ID, CAR_ID);
        vets.setRequiresParticipantApproval(false);
        service.registerCar(STRANGER, EVENT_ID, CAR_ID);

        ArgumentCaptor<MapEventParticipantEntity> captor = ArgumentCaptor.forClass(MapEventParticipantEntity.class);
        verify(participantRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(MapEventParticipantEntity::getStatus)
                .containsExactly("pending", "accepted");
    }

    @Test
    void registrationIsClosedOnceTheAcceptedLineUpHitsCapacity() {
        MapEventEntity capped = approvedUpcoming();
        capped.setMaxParticipantCapacity(2);
        ReflectionTestUtils.setField(capped, "attendingCarsCount", 2);
        existing(capped);
        when(garageService.findCarOwnerIds(List.of(CAR_ID))).thenReturn(Map.of(CAR_ID, STRANGER));
        when(participantRepository.findById(any())).thenReturn(Optional.empty());

        assertThatExceptionOfType(EventClosedException.class)
                .isThrownBy(() -> service.registerCar(STRANGER, EVENT_ID, CAR_ID));
    }

    @Test
    void reRegisteringAnExistingEntryIgnoresCapacity() {
        MapEventEntity capped = approvedUpcoming();
        capped.setMaxParticipantCapacity(2);
        ReflectionTestUtils.setField(capped, "attendingCarsCount", 2);
        existing(capped);
        when(garageService.findCarOwnerIds(List.of(CAR_ID))).thenReturn(Map.of(CAR_ID, STRANGER));
        MapEventParticipantEntity existingRow = participant(STRANGER, CAR_ID, MapEventParticipantEntity.ACCEPTED);
        when(participantRepository.findById(any())).thenReturn(Optional.of(existingRow));

        service.registerCar(STRANGER, EVENT_ID, CAR_ID);

        verify(participantRepository).save(existingRow);
        assertThat(existingRow.getStatus()).isEqualTo("accepted");
    }

    @Test
    void onlyAnOrganizerDecidesOnAnEnteredCar() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.decideParticipant(STRANGER, EVENT_ID, CAR_ID, "accepted", null));
    }

    @Test
    void aDecisionMustBeAcceptedOrRejected() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.decideParticipant(CREATOR, EVENT_ID, CAR_ID, "pending", null));
    }

    @Test
    void decideParticipantRefusesARowWithAPendingWithdrawal() {
        existing(approvedUpcoming());
        when(participantRepository.findById(new MapEventParticipantId(EVENT_ID, CAR_ID)))
                .thenReturn(Optional.of(participant(STRANGER, CAR_ID, MapEventParticipantEntity.WITHDRAWN)));

        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.decideParticipant(CREATOR, EVENT_ID, CAR_ID, "rejected", "not a fit"));
    }

    @Test
    void rejectingAnEnteredCarRequiresAReason() {
        existing(approvedUpcoming());
        when(participantRepository.findById(new MapEventParticipantId(EVENT_ID, CAR_ID)))
                .thenReturn(Optional.of(participant(STRANGER, CAR_ID, MapEventParticipantEntity.PENDING)));

        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.decideParticipant(CREATOR, EVENT_ID, CAR_ID, "rejected", "  "));
    }

    @Test
    void rejectingAnEnteredCarStoresTheReasonAndTellsTheOwner() {
        existing(approvedUpcoming());
        MapEventParticipantEntity row = participant(STRANGER, CAR_ID, MapEventParticipantEntity.PENDING);
        when(participantRepository.findById(new MapEventParticipantId(EVENT_ID, CAR_ID)))
                .thenReturn(Optional.of(row));

        service.decideParticipant(CREATOR, EVENT_ID, CAR_ID, "rejected", "not a fit");

        assertThat(row.getStatus()).isEqualTo("rejected");
        assertThat(row.getRejectionReason()).isEqualTo("not a fit");
        verify(events).publishEvent(any(com.tweakdapp.backend.mapevents.MapEventCarDecidedEvent.class));
    }

    @Test
    void acceptingAnEnteredCarClearsAnyPriorRejectionReason() {
        existing(approvedUpcoming());
        MapEventParticipantEntity row = participant(STRANGER, CAR_ID, MapEventParticipantEntity.REJECTED);
        row.setRejectionReason("not a fit");
        when(participantRepository.findById(new MapEventParticipantId(EVENT_ID, CAR_ID)))
                .thenReturn(Optional.of(row));

        service.decideParticipant(CREATOR, EVENT_ID, CAR_ID, "accepted", null);

        assertThat(row.getStatus()).isEqualTo("accepted");
        assertThat(row.getRejectionReason()).isNull();
    }

    @Test
    void listMyParticipantsReturnsTheCallersOwnRowsWhateverTheirStatus() {
        existing(approvedUpcoming());
        MapEventParticipantEntity rejected = participant(STRANGER, CAR_ID, MapEventParticipantEntity.REJECTED);
        rejected.setRejectionReason("not a fit");
        when(participantRepository.findByIdEventIdAndOwnerId(EVENT_ID, STRANGER)).thenReturn(List.of(rejected));
        when(garageService.findCarsByIds(any())).thenReturn(List.of(carSummary(CAR_ID)));

        List<MapEventParticipantDto> mine = service.listMyParticipants(STRANGER, EVENT_ID);

        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).status()).isEqualTo("rejected");
        assertThat(mine.get(0).rejectionReason()).isEqualTo("not a fit");
    }

    // ---- withdrawing a still-pending registration ----------------------------

    @Test
    void aPendingRegistrationCanBeWithdrawnDirectly() {
        existing(approvedUpcoming());
        MapEventParticipantEntity row = participant(STRANGER, CAR_ID, MapEventParticipantEntity.PENDING);
        when(participantRepository.findById(new MapEventParticipantId(EVENT_ID, CAR_ID)))
                .thenReturn(Optional.of(row));

        service.withdrawCar(STRANGER, EVENT_ID, CAR_ID);

        verify(participantRepository).delete(row);
    }

    @Test
    void anAcceptedRegistrationCannotBeWithdrawnDirectly() {
        when(participantRepository.findById(new MapEventParticipantId(EVENT_ID, CAR_ID)))
                .thenReturn(Optional.of(participant(STRANGER, CAR_ID, MapEventParticipantEntity.ACCEPTED)));

        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.withdrawCar(STRANGER, EVENT_ID, CAR_ID));
        verify(participantRepository, never()).delete(any());
    }

    // ---- withdrawal requests --------------------------------------------------

    @Test
    void requestingWithdrawalRequiresAnAcceptedRegistration() {
        existing(approvedUpcoming());
        when(participantRepository.findByIdEventIdAndOwnerIdAndStatus(
                EVENT_ID, STRANGER, MapEventParticipantEntity.ACCEPTED)).thenReturn(List.of());

        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.requestWithdrawal(STRANGER, EVENT_ID, "can't make it"));
    }

    @Test
    void requestingWithdrawalIsClosedOnceTheEventHasFinished() {
        existing(event(MapEventEntity.APPROVAL_ACCEPTED, MapEventEntity.STATUS_PREVIOUS,
                Instant.now().minus(5, ChronoUnit.HOURS), null));

        assertThatExceptionOfType(EventClosedException.class)
                .isThrownBy(() -> service.requestWithdrawal(STRANGER, EVENT_ID, null));
    }

    @Test
    void requestingWithdrawalFlagsEveryAcceptedRowWithoutDeletingThem() {
        existing(approvedUpcoming());
        MapEventParticipantEntity row = participant(STRANGER, CAR_ID, MapEventParticipantEntity.ACCEPTED);
        when(participantRepository.findByIdEventIdAndOwnerIdAndStatus(
                EVENT_ID, STRANGER, MapEventParticipantEntity.ACCEPTED)).thenReturn(List.of(row));

        service.requestWithdrawal(STRANGER, EVENT_ID, "  can't make it  ");

        assertThat(row.getStatus()).isEqualTo(MapEventParticipantEntity.WITHDRAWN);
        assertThat(row.getWithdrawNote()).isEqualTo("can't make it");
        verify(participantRepository, never()).delete(any());
        verify(participantRepository, never()).deleteAll(any());
        // CREATOR is the only individual organizer and is not the actor, so they get notified.
        verify(events).publishEvent(any(MapEventWithdrawalRequestedEvent.class));
    }

    @Test
    void requestingWithdrawalDoesNotNotifyWhenTheActorIsTheOnlyOrganizer() {
        existing(approvedUpcoming());
        when(participantRepository.findByIdEventIdAndOwnerIdAndStatus(
                EVENT_ID, CREATOR, MapEventParticipantEntity.ACCEPTED))
                .thenReturn(List.of(participant(CREATOR, CAR_ID, MapEventParticipantEntity.ACCEPTED)));
        when(garageService.findCarsByIds(any())).thenReturn(List.of());

        service.requestWithdrawal(CREATOR, EVENT_ID, null);

        verify(events, never()).publishEvent(any(MapEventWithdrawalRequestedEvent.class));
    }

    // ---- organizer review of withdrawal requests -------------------------------

    @Test
    void onlyAnOrganizerCanListWithdrawalRequests() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.listWithdrawalRequests(STRANGER, EVENT_ID));
    }

    @Test
    void withdrawalRequestsAreGroupedByOwner() {
        existing(approvedUpcoming());
        UUID otherCar = UUID.randomUUID();
        MapEventParticipantEntity strangerRow = participant(STRANGER, CAR_ID, MapEventParticipantEntity.WITHDRAWN);
        strangerRow.setWithdrawNote("moving away");
        MapEventParticipantEntity strangerSecondCarRow =
                participant(STRANGER, otherCar, MapEventParticipantEntity.WITHDRAWN);
        strangerSecondCarRow.setWithdrawNote("moving away");
        MapEventParticipantEntity coOrganizerRow =
                participant(CO_ORGANIZER, UUID.randomUUID(), MapEventParticipantEntity.WITHDRAWN);
        when(participantRepository.findByIdEventIdAndStatus(EVENT_ID, MapEventParticipantEntity.WITHDRAWN))
                .thenReturn(List.of(strangerRow, strangerSecondCarRow, coOrganizerRow));
        when(garageService.findCarsByIds(any())).thenReturn(List.of(
                carSummary(CAR_ID), carSummary(otherCar), carSummary(coOrganizerRow.getId().getCarId())));
        when(profileService.findByIds(any())).thenReturn(List.of(
                new ProfileSearchResultDto(STRANGER, "Stranger Name", "stranger", null),
                new ProfileSearchResultDto(CO_ORGANIZER, "Co Organizer Name", "co_organizer", null)));

        List<MapEventWithdrawalRequestDto> requests = service.listWithdrawalRequests(CREATOR, EVENT_ID);

        assertThat(requests).hasSize(2);
        MapEventWithdrawalRequestDto strangerRequest = requests.stream()
                .filter(r -> r.owner().id().equals(STRANGER)).findFirst().orElseThrow();
        assertThat(strangerRequest.cars()).hasSize(2);
        assertThat(strangerRequest.note()).isEqualTo("moving away");
    }

    @Test
    void approvingAWithdrawalRequiresOneToExist() {
        existing(approvedUpcoming());
        when(participantRepository.findByIdEventIdAndOwnerIdAndStatus(
                EVENT_ID, STRANGER, MapEventParticipantEntity.WITHDRAWN)).thenReturn(List.of());

        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.approveWithdrawal(CREATOR, EVENT_ID, STRANGER));
    }

    @Test
    void approvingAWithdrawalHardDeletesTheRowsAndNotifiesTheOwner() {
        existing(approvedUpcoming());
        List<MapEventParticipantEntity> rows =
                List.of(participant(STRANGER, CAR_ID, MapEventParticipantEntity.WITHDRAWN));
        when(participantRepository.findByIdEventIdAndOwnerIdAndStatus(
                EVENT_ID, STRANGER, MapEventParticipantEntity.WITHDRAWN)).thenReturn(rows);

        service.approveWithdrawal(CREATOR, EVENT_ID, STRANGER);

        verify(participantRepository).deleteAll(rows);
        verify(events).publishEvent(any(MapEventWithdrawalDecidedEvent.class));
    }

    @Test
    void onlyAnOrganizerCanDecideAWithdrawalRequest() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.approveWithdrawal(STRANGER, EVENT_ID, CO_ORGANIZER));
        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.rejectWithdrawal(STRANGER, EVENT_ID, CO_ORGANIZER));
    }

    @Test
    void rejectingAWithdrawalRevertsToAcceptedAndKeepsTheNote() {
        existing(approvedUpcoming());
        MapEventParticipantEntity row = participant(STRANGER, CAR_ID, MapEventParticipantEntity.WITHDRAWN);
        row.setWithdrawNote("changed my mind about leaving");
        when(participantRepository.findByIdEventIdAndOwnerIdAndStatus(
                EVENT_ID, STRANGER, MapEventParticipantEntity.WITHDRAWN)).thenReturn(List.of(row));

        service.rejectWithdrawal(CREATOR, EVENT_ID, STRANGER);

        assertThat(row.getStatus()).isEqualTo(MapEventParticipantEntity.ACCEPTED);
        // The note is a historical record of the attempt — kept, not cleared, on rejection.
        assertThat(row.getWithdrawNote()).isEqualTo("changed my mind about leaving");
        verify(participantRepository, never()).delete(any());
        verify(events).publishEvent(any(MapEventWithdrawalDecidedEvent.class));
    }

    // ---- the public entry list still shows pending withdrawals ----------------

    @Test
    void theDefaultParticipantListIncludesWithdrawnCarsAlongsideAccepted() {
        existing(approvedUpcoming());
        when(participantRepository.findLineupPage(eq(EVENT_ID), any(), any(), any())).thenReturn(List.of());

        service.listParticipants(STRANGER, EVENT_ID, null, null, 20);

        verify(participantRepository).findLineupPage(eq(EVENT_ID), any(), any(), any());
        verify(participantRepository, never()).findPage(any(), any(), any(), any(), any());
    }

    @Test
    void aNonOrganizerMayFilterTheListToWithdrawnOnly() {
        existing(approvedUpcoming());
        when(participantRepository.findPage(eq(EVENT_ID), eq(MapEventParticipantEntity.WITHDRAWN), any(), any(), any()))
                .thenReturn(List.of());

        service.listParticipants(STRANGER, EVENT_ID, "withdrawn", null, 20);
        // No exception: a pending withdrawal carries no stigma, unlike pending/rejected entries.
    }

    @Test
    void aNonOrganizerCannotFilterTheListToPending() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.listParticipants(STRANGER, EVENT_ID, "pending", null, 20));
    }

    // ---- cover image --------------------------------------------------------

    @Test
    void aCoverKeyBelongingToAnotherEventIsRejected() {
        existing(event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null));

        // Otherwise one event could claim an object uploaded under another event's prefix.
        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.saveCoverImageKey(CREATOR, EVENT_ID,
                        "events/" + UUID.randomUUID() + "/cover.webp"));
    }

    @Test
    void aCoverKeyUnderTheEventsOwnPrefixIsAccepted() {
        MapEventEntity pending = event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null);
        existing(pending);

        String key = "events/" + EVENT_ID + "/abc.webp";
        service.saveCoverImageKey(CREATOR, EVENT_ID, key);

        assertThat(pending.getCoverImageKey()).isEqualTo(key);
    }

    // ---- rules ----------------------------------------------------------------

    private static MapEventCategoryEntity socialCategory() {
        MapEventCategoryEntity category = new MapEventCategoryEntity();
        category.setId("social");
        category.setLabel("Social");
        category.setAvailable(true);
        return category;
    }

    private static CreateMapEventRequest createRequest(List<String> rules) {
        return new CreateMapEventRequest(
                "social", "Cars & Coffee", "Casual meetup", "Central park",
                46.77, 23.62, Instant.now().plus(2, ChronoUnit.DAYS), null,
                null, null, null, rules);
    }

    @Test
    void creatingAnEventWithRulesSavesThemInTheSameTransaction() {
        when(categoryRepository.findById("social")).thenReturn(Optional.of(socialCategory()));

        service.createEvent(CREATOR, createRequest(List.of("No burnouts", "Park in marked bays")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MapEventRuleEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(MapEventRuleEntity::getRule)
                .containsExactly("No burnouts", "Park in marked bays");
        assertThat(captor.getValue()).extracting(MapEventRuleEntity::getSortOrder)
                .containsExactly((short) 0, (short) 1);
        // No separate PUT /rules call needed: creation and rules land in the one transaction.
        verify(ruleRepository, never()).deleteByEventId(any());
    }

    @Test
    void creatingAnEventWithoutRulesDoesNotTouchTheRuleRepository() {
        when(categoryRepository.findById("social")).thenReturn(Optional.of(socialCategory()));

        service.createEvent(CREATOR, createRequest(null));

        verify(ruleRepository, never()).saveAll(any());
    }

    @Test
    void rulesCanBeReplacedWhileTheEventIsEditable() {
        MapEventEntity pending = event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null);
        existing(pending);

        service.replaceRules(CREATOR, EVENT_ID, List.of("No burnouts", "Park in marked bays"));

        verify(ruleRepository).deleteByEventId(EVENT_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MapEventRuleEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(ruleRepository).saveAll(captor.capture());
        List<MapEventRuleEntity> saved = captor.getValue();
        assertThat(saved).extracting(MapEventRuleEntity::getRule)
                .containsExactly("No burnouts", "Park in marked bays");
        assertThat(saved).extracting(MapEventRuleEntity::getSortOrder)
                .containsExactly((short) 0, (short) 1);
        assertThat(saved).allMatch(row -> row.getEventId().equals(EVENT_ID));
    }

    @Test
    void replacingRulesWithAnEmptyListClearsThem() {
        existing(event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null));

        service.replaceRules(CREATOR, EVENT_ID, List.of());

        verify(ruleRepository).deleteByEventId(EVENT_ID);
        verify(ruleRepository).saveAll(List.of());
    }

    @Test
    void rulesCannotBeReplacedOnAnApprovedEvent() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(EventNotEditableException.class)
                .isThrownBy(() -> service.replaceRules(CREATOR, EVENT_ID, List.of("New rule")));
        verify(ruleRepository, never()).deleteByEventId(any());
    }

    @Test
    void aStrangerCannotReplaceRules() {
        existing(event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null));

        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.replaceRules(STRANGER, EVENT_ID, List.of("Sneaky rule")));
        verify(ruleRepository, never()).deleteByEventId(any());
    }

    @Test
    void aCoOrganizerCanReplaceRules() {
        MapEventEntity pending = event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null);
        existing(pending);

        service.replaceRules(CO_ORGANIZER, EVENT_ID, List.of("No burnouts"));

        verify(ruleRepository).deleteByEventId(EVENT_ID);
    }

    // ---- map search bounds --------------------------------------------------

    @Test
    void mapSearchBoundsAreEnforced() {
        assertThatExceptionOfType(InvalidSearchAreaException.class)
                .isThrownBy(() -> service.findNearby(91, 23.6, 25, null, 100));
        assertThatExceptionOfType(InvalidSearchAreaException.class)
                .isThrownBy(() -> service.findNearby(46.7, 181, 25, null, 100));
        // An unbounded radius would turn the spatial index into a full scan.
        assertThatExceptionOfType(InvalidSearchAreaException.class)
                .isThrownBy(() -> service.findNearby(46.7, 23.6, 5000, null, 100));
        assertThatExceptionOfType(InvalidSearchAreaException.class)
                .isThrownBy(() -> service.findNearby(46.7, 23.6, 25, null, 100_000));
        assertThatExceptionOfType(InvalidSearchAreaException.class)
                .isThrownBy(() -> service.findNearby(Double.NaN, 23.6, 25, null, 100));
    }
}
