package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.business.BusinessService;
import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.mapevents.dto.MapEventPageDto;
import com.tweakdapp.backend.mapevents.dto.MapEventPinDto;
import com.tweakdapp.backend.mapevents.exception.InvalidCursorException;
import com.tweakdapp.backend.mapevents.exception.InvalidMapEventException;
import com.tweakdapp.backend.mapevents.exception.InvalidSearchAreaException;
import com.tweakdapp.backend.mapevents.internal.repositories.CarMeetRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestEntryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventAttendeeRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventCategoryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventOrganizerRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventParticipantRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRuleRepository;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The service half of the map's event search: the status filter (its default and its refusal of
 * unknown values), query-text guard rails, wildcard escaping and the keyset cursor round-trip. The
 * SQL — including the clock-derived phase — is covered against PostGIS by
 * {@code MapEventRepositoryIT}.
 */
class MapEventsServiceImplSearchTest {

    private static final double LAT = 46.7712;
    private static final double LNG = 23.6236;
    private static final UUID FIRST_PAGE_ID = new UUID(0, 0);

    private MapEventRepository eventRepository;
    private MapEventsServiceImpl service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(MapEventRepository.class);
        service = new MapEventsServiceImpl(
                eventRepository, mock(MapEventCategoryRepository.class), mock(CarMeetRepository.class),
                mock(MapEventOrganizerRepository.class), mock(MapEventAttendeeRepository.class),
                mock(MapEventParticipantRepository.class), mock(MapEventRuleRepository.class),
                mock(ProfileService.class), mock(GarageService.class), mock(BusinessService.class),
                mock(StorageService.class), mock(ApplicationEventPublisher.class),
                mock(MapboxGeocodingClient.class), mock(ContestFinalizer.class),
                mock(ContestEntryRepository.class));
    }

    private void repositoryReturns(List<MapEventRepository.SearchPinRow> rows) {
        when(eventRepository.searchVisible(anyString(), anyDouble(), anyDouble(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyDouble(), any(), anyInt()))
                .thenReturn(rows);
    }

    @Test
    void withNoStatusItSearchesLiveAndUpcomingOnly() {
        repositoryReturns(List.of());

        service.search("meet", LAT, LNG, null, null, 20);

        // includeLive, includeUpcoming, includePrevious
        verify(eventRepository).searchVisible(eq("%meet%"), eq(LAT), eq(LNG), eq(true), eq(true), eq(false),
                eq(-1.0), eq(FIRST_PAGE_ID), eq(21));
    }

    @Test
    void blankStatusesCountAsNoFilter() {
        repositoryReturns(List.of());

        service.search("meet", LAT, LNG, List.of("", " "), null, 20);

        verify(eventRepository).searchVisible(anyString(), anyDouble(), anyDouble(), eq(true), eq(true), eq(false),
                anyDouble(), any(), anyInt());
    }

    @Test
    void theStatusFilterIsPassedThroughCaseInsensitively() {
        repositoryReturns(List.of());

        service.search("meet", LAT, LNG, List.of("PREVIOUS"), null, 20);

        verify(eventRepository).searchVisible(anyString(), anyDouble(), anyDouble(), eq(false), eq(false), eq(true),
                anyDouble(), any(), anyInt());
    }

    /** Hidden and canceled are real stored statuses, but never searchable phases. */
    @Test
    void anUnknownStatusIsRefusedRatherThanIgnored() {
        assertThatThrownBy(() -> service.search("meet", LAT, LNG, List.of("live", "canceled"), null, 20))
                .isInstanceOf(InvalidMapEventException.class);
    }

    @Test
    void aQueryShorterThanTwoCharactersReturnsAnEmptyPageWithoutTouchingTheDatabase() {
        MapEventPageDto<MapEventPinDto> page = service.search(" m ", LAT, LNG, null, null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verifyNoInteractions(eventRepository);
    }

    @Test
    void wildcardsInTheQueryAreEscaped() {
        repositoryReturns(List.of());

        service.search("50%_", LAT, LNG, null, null, 20);

        verify(eventRepository).searchVisible(eq("%50\\%\\_%"), anyDouble(), anyDouble(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyDouble(), any(), anyInt());
    }

    @Test
    void aFullPageCarriesACursorThatResumesAfterItsLastRow() {
        List<MapEventRepository.SearchPinRow> rows = rows(3);
        repositoryReturns(rows);

        MapEventPageDto<MapEventPinDto> first = service.search("meet", LAT, LNG, null, null, 2);

        assertThat(first.items()).hasSize(2);
        assertThat(first.items().getFirst().status()).isEqualTo("live");
        assertThat(first.nextCursor()).isNotNull();

        repositoryReturns(List.of());
        service.search("meet", LAT, LNG, null, first.nextCursor(), 2);

        verify(eventRepository).searchVisible(anyString(), anyDouble(), anyDouble(), anyBoolean(), anyBoolean(),
                anyBoolean(), eq(rows.get(1).getDistanceMetres()), eq(rows.get(1).getId()), eq(3));
    }

    @Test
    void theLastPageHasNoCursor() {
        repositoryReturns(rows(1));

        assertThat(service.search("meet", LAT, LNG, null, null, 20).nextCursor()).isNull();
    }

    @Test
    void anUnparseableCursorIsRejected() {
        assertThatThrownBy(() -> service.search("meet", LAT, LNG, null, "garbage", 20))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void anOutOfRangeCentreIsRejected() {
        assertThatThrownBy(() -> service.search("meet", LAT, 181, null, null, 20))
                .isInstanceOf(InvalidSearchAreaException.class);
    }

    private static List<MapEventRepository.SearchPinRow> rows(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> (MapEventRepository.SearchPinRow) new Row(UUID.randomUUID(), 250.25 * (i + 1)))
                .toList();
    }

    private record Row(UUID id, double distance) implements MapEventRepository.SearchPinRow {
        @Override public UUID getId() { return id; }
        @Override public String getTitle() { return "Sunday meet"; }
        @Override public String getCategoryId() { return "car_meet"; }
        @Override public String getCategoryLabel() { return "Car meet"; }
        @Override public double getLat() { return LAT; }
        @Override public double getLng() { return LNG; }
        @Override public String getLocationName() { return "Iulius Mall parking"; }
        @Override public String getCoverImageKey() { return null; }
        @Override public Instant getStartsAt() { return Instant.now(); }
        @Override public Instant getEndsAt() { return null; }
        @Override public String getStatus() { return "live"; }
        @Override public int getAttendeesCount() { return 0; }
        @Override public int getAttendingCarsCount() { return 0; }
        @Override public Integer getMaxParticipantCapacity() { return null; }
        @Override public double getDistanceMetres() { return distance; }
    }
}
