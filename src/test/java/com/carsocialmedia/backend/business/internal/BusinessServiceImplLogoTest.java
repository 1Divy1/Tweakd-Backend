package com.carsocialmedia.backend.business.internal;

import com.carsocialmedia.backend.business.dto.BusinessDto;
import com.carsocialmedia.backend.business.dto.BusinessMapPinDto;
import com.carsocialmedia.backend.business.internal.entities.BusinessAccountEntity;
import com.carsocialmedia.backend.business.internal.entities.BusinessTypeOptionEntity;
import com.carsocialmedia.backend.business.internal.repositories.BusinessAccountRepository;
import com.carsocialmedia.backend.business.internal.repositories.BusinessHoursRepository;
import com.carsocialmedia.backend.business.internal.repositories.BusinessTypeOptionRepository;
import com.carsocialmedia.backend.shared.geo.CityRepository;
import com.carsocialmedia.backend.shared.geo.GeoSupport;
import com.carsocialmedia.backend.storage.StorageBucket;
import com.carsocialmedia.backend.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code business_accounts.logo_url} holds an R2 object key, not a URL. Both read paths must
 * resolve it through {@link StorageService} before the DTO leaves the module — a raw key reaching
 * the app renders as a broken image on every map pin, which is silent (no error, no failing
 * request), so it is pinned here.
 */
class BusinessServiceImplLogoTest {

    private static final UUID BUSINESS_ID = UUID.fromString("967d6b34-7b61-4d8a-a7df-8e670c79d680");
    private static final String KEY = "business/967d6b34-7b61-4d8a-a7df-8e670c79d680/Logo.webp";
    private static final String URL = "https://business.northbyte.co/" + KEY;

    private BusinessAccountRepository businessRepository;
    private BusinessHoursRepository hoursRepository;
    private CityRepository cityRepository;
    private StorageService storageService;
    private BusinessServiceImpl service;

    @BeforeEach
    void setUp() {
        businessRepository = mock(BusinessAccountRepository.class);
        hoursRepository = mock(BusinessHoursRepository.class);
        cityRepository = mock(CityRepository.class);
        storageService = mock(StorageService.class);
        service = new BusinessServiceImpl(
                businessRepository,
                hoursRepository,
                mock(BusinessTypeOptionRepository.class),
                cityRepository,
                storageService);

        when(storageService.publicUrl(StorageBucket.BUSINESS, KEY)).thenReturn(URL);
    }

    @Test
    void mapPinsExposeTheResolvedLogoUrlNotTheStoredKey() {
        when(businessRepository.findVisibleNearby(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(new Row(KEY)));
        when(hoursRepository.findByBusinessIdIn(any())).thenReturn(List.of());

        List<BusinessMapPinDto> pins = service.findNearby(46.7712, 23.6236, 25.0, null, 200);

        assertThat(pins).singleElement()
                .extracting(BusinessMapPinDto::logoUrl)
                .isEqualTo(URL);
    }

    @Test
    void theBusinessProfileExposesTheResolvedLogoUrl() {
        when(businessRepository.findVisibleById(BUSINESS_ID)).thenReturn(Optional.of(entity(KEY)));
        when(hoursRepository.findByBusinessIdOrderByWeekdayAsc(BUSINESS_ID)).thenReturn(List.of());
        when(cityRepository.findById("cluj-napoca")).thenReturn(Optional.empty());

        BusinessDto dto = service.getBusiness(BUSINESS_ID);

        assertThat(dto.logoUrl()).isEqualTo(URL);
    }

    @Test
    void anAlreadyAbsoluteUrlIsPassedThroughUntouched() {
        String external = "https://cdn.partner.example/logo.png";
        when(businessRepository.findVisibleById(BUSINESS_ID)).thenReturn(Optional.of(entity(external)));
        when(hoursRepository.findByBusinessIdOrderByWeekdayAsc(BUSINESS_ID)).thenReturn(List.of());

        assertThat(service.getBusiness(BUSINESS_ID).logoUrl()).isEqualTo(external);
        verifyNoInteractions(storageService);
    }

    @Test
    void aBusinessWithoutALogoStaysNull() {
        when(businessRepository.findVisibleById(BUSINESS_ID)).thenReturn(Optional.of(entity(null)));
        when(hoursRepository.findByBusinessIdOrderByWeekdayAsc(BUSINESS_ID)).thenReturn(List.of());

        assertThat(service.getBusiness(BUSINESS_ID).logoUrl()).isNull();
        verifyNoInteractions(storageService);
    }

    // ------------------------------------------------------------------

    /** A hand-written projection row; the real one is a Spring Data interface proxy. */
    private record Row(String logoUrl) implements BusinessAccountRepository.MapPinRow {
        public UUID getId() { return BUSINESS_ID; }
        public String getName() { return "Willy Wash"; }
        public String getTypeId() { return "tire_shop"; }
        public String getTypeLabel() { return "Tire shop"; }
        public double getLat() { return 46.7874479284246; }
        public double getLng() { return 23.6308604799099; }
        public String getLogoUrl() { return logoUrl; }
        public BigDecimal getAverageRating() { return new BigDecimal("5.0"); }
        public int getReviewCount() { return 100; }
        public String getTimezone() { return "Europe/Bucharest"; }
    }

    private static BusinessAccountEntity entity(String logoUrl) {
        BusinessTypeOptionEntity type = new BusinessTypeOptionEntity();
        type.setId("tire_shop");
        type.setType("Tire shop");

        BusinessAccountEntity business = new BusinessAccountEntity();
        business.setId(BUSINESS_ID);
        business.setName("Willy Wash");
        business.setType(type);
        business.setLogoUrl(logoUrl);
        business.setCityId("cluj-napoca");
        business.setLocation(GeoSupport.point(46.7874479284246, 23.6308604799099));
        business.setTimezone("Europe/Bucharest");
        business.setAverageRating(new BigDecimal("5.0"));
        return business;
    }
}
