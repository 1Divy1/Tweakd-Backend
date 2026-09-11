package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.business.BusinessService;
import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.mapevents.MapEventWithdrawalDecidedEvent;
import com.tweakdapp.backend.mapevents.MapEventWithdrawalRequestedEvent;
import com.tweakdapp.backend.mapevents.ParticipantCardsReadyEvent;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Marking an event finished is what brings its participant cards into existence, so it is also when
 * each participant hears about theirs — exactly once per person, however many cars they brought, and
 * never again on a repeat tap.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParticipantCardsReadyTest {

    private static final UUID ORGANIZER = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000d2");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

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
    private MapEventEntity event;

    @BeforeEach
    void setUp() {
        service = new MapEventsServiceImpl(
                eventRepository, categoryRepository, carMeetRepository, organizerRepository,
                attendeeRepository, participantRepository, ruleRepository, profileService, garageService,
                businessService, storageService, events, mapboxGeocodingClient, contestFinalizer,
                contestEntryRepository);

        // Not a car meet, so assembling the response needs no car-meet detail row.
        MapEventCategoryEntity category = new MapEventCategoryEntity();
        category.setId("other");
        category.setLabel("Other");
        category.setAvailable(true);

        event = new MapEventEntity();
        event.setId(EVENT_ID);
        event.setCategory(category);
        event.setTitle("Casino Square Cars & Coffee");
        event.setStatus(MapEventEntity.STATUS_LIVE);
        event.setApprovalStatus(MapEventEntity.APPROVAL_ACCEPTED);
        event.setStartsAt(Instant.now().minus(3, ChronoUnit.HOURS));
        event.setCreatedBy(ORGANIZER);
        event.setLocation(new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(7.42, 43.74)));
        when(eventRepository.findWithCategoryById(EVENT_ID)).thenReturn(Optional.of(event));
        when(organizerRepository.existsByEventIdAndIndividualOrganizerId(EVENT_ID, ORGANIZER)).thenReturn(true);

        // OWNER brought two cars; they still get one notification.
        when(participantRepository.findByIdEventIdAndStatus(EVENT_ID, MapEventParticipantEntity.ACCEPTED))
                .thenReturn(List.of(participant(OWNER, "b1"), participant(OWNER, "b2"), participant(OTHER_OWNER, "b3")));
    }

    @Test
    void finishingTheEventTellsEachParticipantOnceThatTheirCardIsReady() {
        service.markFinished(ORGANIZER, EVENT_ID);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events, atLeastOnce()).publishEvent(published.capture());
        assertThat(published.getAllValues())
                .filteredOn(ParticipantCardsReadyEvent.class::isInstance)
                .singleElement()
                .satisfies(e -> {
                    ParticipantCardsReadyEvent ready = (ParticipantCardsReadyEvent) e;
                    assertThat(ready.eventId()).isEqualTo(EVENT_ID);
                    assertThat(ready.ownerIds()).containsExactlyInAnyOrder(OWNER, OTHER_OWNER);
                });
    }

    @Test
    void aRepeatFinishDoesNotNotifyAgain() {
        service.markFinished(ORGANIZER, EVENT_ID);
        clearInvocations(events);

        service.markFinished(ORGANIZER, EVENT_ID);

        verify(events, never()).publishEvent(any(ParticipantCardsReadyEvent.class));
    }

    @Test
    void anEventWithNoAcceptedCarsNotifiesNobody() {
        when(participantRepository.findByIdEventIdAndStatus(EVENT_ID, MapEventParticipantEntity.ACCEPTED))
                .thenReturn(List.of());

        service.markFinished(ORGANIZER, EVENT_ID);

        verify(events, never()).publishEvent(any(ParticipantCardsReadyEvent.class));
    }

    private static MapEventParticipantEntity participant(UUID ownerId, String carSuffix) {
        MapEventParticipantEntity participant = new MapEventParticipantEntity();
        participant.setId(new MapEventParticipantId(EVENT_ID,
                UUID.fromString("00000000-0000-0000-0000-0000000000" + carSuffix)));
        participant.setOwnerId(ownerId);
        participant.setStatus(MapEventParticipantEntity.ACCEPTED);
        return participant;
    }
}
