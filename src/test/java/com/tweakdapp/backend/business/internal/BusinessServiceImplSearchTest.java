package com.tweakdapp.backend.business.internal;

import com.tweakdapp.backend.business.dto.BusinessSearchPageDto;
import com.tweakdapp.backend.business.exception.InvalidBusinessCursorException;
import com.tweakdapp.backend.business.exception.InvalidSearchAreaException;
import com.tweakdapp.backend.business.internal.repositories.BusinessAccountRepository;
import com.tweakdapp.backend.business.internal.repositories.BusinessHoursRepository;
import com.tweakdapp.backend.business.internal.repositories.BusinessTypeOptionRepository;
import com.tweakdapp.backend.shared.geo.CityRepository;
import com.tweakdapp.backend.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The service half of the map's business search: the query-text guard rails, wildcard escaping,
 * page-size clamping and the keyset cursor round-trip. The SQL itself is covered against PostGIS by
 * {@code BusinessAccountRepositoryIT}.
 */
class BusinessServiceImplSearchTest {

    private static final double LAT = 46.7712;
    private static final double LNG = 23.6236;

    private BusinessAccountRepository businessRepository;
    private BusinessServiceImpl service;

    @BeforeEach
    void setUp() {
        businessRepository = mock(BusinessAccountRepository.class);
        BusinessHoursRepository hoursRepository = mock(BusinessHoursRepository.class);
        when(hoursRepository.findByBusinessIdIn(any())).thenReturn(List.of());
        service = new BusinessServiceImpl(
                businessRepository,
                hoursRepository,
                mock(BusinessTypeOptionRepository.class),
                mock(CityRepository.class),
                mock(StorageService.class));
    }

    private void repositoryReturns(List<BusinessAccountRepository.SearchPinRow> rows) {
        when(businessRepository.searchVisible(anyString(), anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(rows);
    }

    @Test
    void aQueryShorterThanTwoCharactersReturnsAnEmptyPageWithoutTouchingTheDatabase() {
        BusinessSearchPageDto page = service.search("  w  ", LAT, LNG, null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        verifyNoInteractions(businessRepository);
    }

    @Test
    void theQueryIsTrimmedAndItsWildcardsAreEscaped() {
        repositoryReturns(List.of());

        service.search("  100%_off\\  ", LAT, LNG, null, 20);

        verify(businessRepository).searchVisible(eq("%100\\%\\_off\\\\%"), eq(LAT), eq(LNG),
                eq(-1.0), eq(new UUID(0, 0)), eq(21));
    }

    @Test
    void theLastPageHasNoCursor() {
        repositoryReturns(rows(3));

        BusinessSearchPageDto page = service.search("wash", LAT, LNG, null, 5);

        assertThat(page.items()).hasSize(3);
        assertThat(page.nextCursor()).isNull();
    }

    /** One row over the page size signals "more"; it is dropped and the cursor points at the last kept row. */
    @Test
    void aFullPageCarriesACursorThatResumesAfterItsLastRow() {
        List<BusinessAccountRepository.SearchPinRow> rows = rows(3);
        repositoryReturns(rows);

        BusinessSearchPageDto first = service.search("wash", LAT, LNG, null, 2);

        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isNotNull();

        repositoryReturns(List.of());
        service.search("wash", LAT, LNG, first.nextCursor(), 2);

        verify(businessRepository).searchVisible(anyString(), eq(LAT), eq(LNG),
                eq(rows.get(1).getDistanceMetres()), eq(rows.get(1).getId()), eq(3));
    }

    @Test
    void thePageSizeIsClampedTo50() {
        repositoryReturns(List.of());

        service.search("wash", LAT, LNG, null, 10_000);

        verify(businessRepository).searchVisible(anyString(), anyDouble(), anyDouble(), anyDouble(), any(), eq(51));
    }

    @Test
    void anUnparseableCursorIsRejected() {
        assertThatThrownBy(() -> service.search("wash", LAT, LNG, "not-a-cursor", 20))
                .isInstanceOf(InvalidBusinessCursorException.class);
    }

    @Test
    void anOutOfRangeCentreIsRejected() {
        assertThatThrownBy(() -> service.search("wash", 91, LNG, null, 20))
                .isInstanceOf(InvalidSearchAreaException.class);
        assertThatThrownBy(() -> service.search("wash", LAT, Double.NaN, null, 20))
                .isInstanceOf(InvalidSearchAreaException.class);
    }

    private static List<BusinessAccountRepository.SearchPinRow> rows(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> (BusinessAccountRepository.SearchPinRow) new Row(UUID.randomUUID(), 100.5 * (i + 1)))
                .toList();
    }

    private record Row(UUID id, double distance) implements BusinessAccountRepository.SearchPinRow {
        @Override public UUID getId() { return id; }
        @Override public String getName() { return "Willy Wash"; }
        @Override public String getTypeId() { return "car_wash"; }
        @Override public String getTypeLabel() { return "Car wash"; }
        @Override public double getLat() { return LAT; }
        @Override public double getLng() { return LNG; }
        @Override public String getLogoUrl() { return null; }
        @Override public BigDecimal getAverageRating() { return BigDecimal.ZERO; }
        @Override public int getReviewCount() { return 0; }
        @Override public String getTimezone() { return "Europe/Bucharest"; }
        @Override public double getDistanceMetres() { return distance; }
    }
}
