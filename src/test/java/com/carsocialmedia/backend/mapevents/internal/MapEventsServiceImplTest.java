package com.carsocialmedia.backend.mapevents.internal;

import com.carsocialmedia.backend.business.BusinessService;
import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.mapevents.dto.MapEventDto;
import com.carsocialmedia.backend.mapevents.dto.request.AddOrganizerRequest;
import com.carsocialmedia.backend.mapevents.dto.request.UpdateMapEventRequest;
import com.carsocialmedia.backend.mapevents.exception.CarNotOwnedException;
import com.carsocialmedia.backend.mapevents.exception.EventClosedException;
import com.carsocialmedia.backend.mapevents.exception.EventNotEditableException;
import com.carsocialmedia.backend.mapevents.exception.InvalidMapEventException;
import com.carsocialmedia.backend.mapevents.exception.InvalidSearchAreaException;
import com.carsocialmedia.backend.mapevents.exception.MapEventNotFoundException;
import com.carsocialmedia.backend.mapevents.exception.NotEventOrganizerException;
import com.carsocialmedia.backend.mapevents.internal.entities.CarMeetEntity;
import com.carsocialmedia.backend.mapevents.internal.entities.MapEventCategoryEntity;
import com.carsocialmedia.backend.mapevents.internal.entities.MapEventEntity;
import com.carsocialmedia.backend.mapevents.internal.entities.MapEventOrganizerEntity;
import com.carsocialmedia.backend.mapevents.internal.repositories.CarMeetRepository;
import com.carsocialmedia.backend.mapevents.internal.repositories.MapEventAttendeeRepository;
import com.carsocialmedia.backend.mapevents.internal.repositories.MapEventCategoryRepository;
import com.carsocialmedia.backend.mapevents.internal.repositories.MapEventOrganizerRepository;
import com.carsocialmedia.backend.mapevents.internal.repositories.MapEventParticipantRepository;
import com.carsocialmedia.backend.mapevents.internal.repositories.MapEventRepository;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.shared.geo.GeoSupport;
import com.carsocialmedia.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    @Mock private ProfileService profileService;
    @Mock private GarageService garageService;
    @Mock private BusinessService businessService;
    @Mock private StorageService storageService;
    @Mock private ApplicationEventPublisher events;

    private MapEventsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MapEventsServiceImpl(
                eventRepository, categoryRepository, carMeetRepository, organizerRepository,
                attendeeRepository, participantRepository, profileService, garageService,
                businessService, storageService, events);
        // @PersistenceContext is field-injected, so a pure unit test has to supply it by hand.
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class));

        // Only the creator and the co-organizer organize this event.
        when(organizerRepository.existsByEventIdAndIndividualOrganizerId(any(), any())).thenReturn(false);
        when(organizerRepository.existsByEventIdAndIndividualOrganizerId(EVENT_ID, CREATOR)).thenReturn(true);
        when(organizerRepository.existsByEventIdAndIndividualOrganizerId(EVENT_ID, CO_ORGANIZER)).thenReturn(true);
        when(organizerRepository.findByEventIdOrderByRoleAscCreatedAtAsc(EVENT_ID))
                .thenReturn(List.of(organizerRow(CREATOR, MapEventOrganizerEntity.ROLE_CREATOR)));
        when(participantRepository.findByIdEventIdAndOwnerId(any(), any())).thenReturn(List.of());
        when(attendeeRepository.findById(any())).thenReturn(Optional.empty());
        when(profileService.findByIds(any())).thenReturn(List.of());
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
                        new UpdateMapEventRequest("New title", null, null, null, null, null, null, null, null)));
    }

    @Test
    void aPendingEventCanBeEditedByAnyOrganizer() {
        MapEventEntity pending = event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null);
        existing(pending);

        service.updateEvent(CO_ORGANIZER, EVENT_ID,
                new UpdateMapEventRequest("New title", null, null, null, null, null, null, null, null));

        assertThat(pending.getTitle()).isEqualTo("New title");
    }

    @Test
    void editingARejectedEventResubmitsItAndClearsTheReason() {
        MapEventEntity rejected = event(MapEventEntity.APPROVAL_REJECTED, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null);
        rejected.setRejectionReason("Location is not specific enough");
        existing(rejected);

        service.updateEvent(CREATOR, EVENT_ID,
                new UpdateMapEventRequest(null, null, "Central car park", null, null, null, null, null, null));

        assertThat(rejected.getApprovalStatus()).isEqualTo(MapEventEntity.APPROVAL_PENDING);
        assertThat(rejected.getRejectionReason()).isNull();
    }

    @Test
    void aStrangerCannotEditAnEvent() {
        existing(event(MapEventEntity.APPROVAL_PENDING, MapEventEntity.STATUS_UPCOMING,
                Instant.now().plus(2, ChronoUnit.DAYS), null));

        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.updateEvent(STRANGER, EVENT_ID,
                        new UpdateMapEventRequest("Hijacked", null, null, null, null, null, null, null, null)));
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
        when(garageService.findCarsByIds(any())).thenReturn(List.of());

        assertThat(service.registerCar(STRANGER, EVENT_ID, CAR_ID).status()).isEqualTo("pending");

        vets.setRequiresParticipantApproval(false);
        assertThat(service.registerCar(STRANGER, EVENT_ID, CAR_ID).status()).isEqualTo("accepted");
    }

    @Test
    void onlyAnOrganizerDecidesOnAnEnteredCar() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(NotEventOrganizerException.class)
                .isThrownBy(() -> service.decideParticipant(STRANGER, EVENT_ID, CAR_ID, "accepted"));
    }

    @Test
    void aDecisionMustBeAcceptedOrRejected() {
        existing(approvedUpcoming());

        assertThatExceptionOfType(InvalidMapEventException.class)
                .isThrownBy(() -> service.decideParticipant(CREATOR, EVENT_ID, CAR_ID, "pending"));
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
