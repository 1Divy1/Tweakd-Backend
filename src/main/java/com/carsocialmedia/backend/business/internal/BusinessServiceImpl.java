package com.carsocialmedia.backend.business.internal;

import com.carsocialmedia.backend.business.BusinessService;
import com.carsocialmedia.backend.business.dto.BusinessDto;
import com.carsocialmedia.backend.business.dto.BusinessHoursDto;
import com.carsocialmedia.backend.business.dto.BusinessMapPinDto;
import com.carsocialmedia.backend.business.dto.BusinessRefDto;
import com.carsocialmedia.backend.business.dto.BusinessTypeOptionDto;
import com.carsocialmedia.backend.business.exception.BusinessNotFoundException;
import com.carsocialmedia.backend.business.exception.InvalidSearchAreaException;
import com.carsocialmedia.backend.business.internal.entities.BusinessAccountEntity;
import com.carsocialmedia.backend.business.internal.entities.BusinessHoursEntity;
import com.carsocialmedia.backend.business.internal.entities.BusinessTypeOptionEntity;
import com.carsocialmedia.backend.business.internal.repositories.BusinessAccountRepository;
import com.carsocialmedia.backend.business.internal.repositories.BusinessHoursRepository;
import com.carsocialmedia.backend.business.internal.repositories.BusinessTypeOptionRepository;
import com.carsocialmedia.backend.shared.geo.CityEntity;
import com.carsocialmedia.backend.shared.geo.CityRepository;
import com.carsocialmedia.backend.shared.geo.GeoSupport;
import com.carsocialmedia.backend.storage.StorageBucket;
import com.carsocialmedia.backend.storage.StorageService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
class BusinessServiceImpl implements BusinessService {

    /**
     * Widest radius a single map query may cover. Beyond this the spatial index stops being
     * selective and the query turns into a scan of the whole table; the map has no use for it
     * either, since a 500 km view shows far more pins than can be rendered.
     */
    private static final double MAX_RADIUS_KM = 500.0;

    /** Hard ceiling on pins per request, so a hand-crafted {@code limit} cannot pull the table. */
    private static final int MAX_LIMIT = 500;

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final BusinessAccountRepository businessRepository;
    private final BusinessHoursRepository hoursRepository;
    private final BusinessTypeOptionRepository typeRepository;
    private final CityRepository cityRepository;
    private final StorageService storageService;

    BusinessServiceImpl(BusinessAccountRepository businessRepository,
                        BusinessHoursRepository hoursRepository,
                        BusinessTypeOptionRepository typeRepository,
                        CityRepository cityRepository,
                        StorageService storageService) {
        this.businessRepository = businessRepository;
        this.hoursRepository = hoursRepository;
        this.typeRepository = typeRepository;
        this.cityRepository = cityRepository;
        this.storageService = storageService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessMapPinDto> findNearby(double lat, double lng, double radiusKm, String typeId, int limit) {
        validateSearchArea(lat, lng, radiusKm, limit);

        String normalisedType = (typeId == null || typeId.isBlank()) ? null : typeId.trim();

        List<BusinessAccountRepository.MapPinRow> rows =
                businessRepository.findVisibleNearby(lat, lng, radiusKm * 1000.0, normalisedType, limit);
        if (rows.isEmpty()) {
            return List.of();
        }

        // One batched lookup for the whole page's hours; see BusinessHoursRepository.
        List<UUID> ids = rows.stream().map(BusinessAccountRepository.MapPinRow::getId).toList();
        Map<UUID, List<BusinessHoursEntity>> hoursByBusiness = hoursRepository.findByBusinessIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(BusinessHoursEntity::getBusinessId));

        // A single "now" for the whole page, so two pins cannot disagree about the current time.
        Instant now = Instant.now();

        return rows.stream()
                .map(row -> new BusinessMapPinDto(
                        row.getId(),
                        row.getName(),
                        row.getTypeId(),
                        row.getTypeLabel(),
                        row.getLat(),
                        row.getLng(),
                        resolveLogoUrl(row.getLogoUrl()),
                        toRating(row.getAverageRating()),
                        row.getReviewCount(),
                        OpeningHours.isOpenAt(
                                hoursByBusiness.getOrDefault(row.getId(), List.of()),
                                row.getTimezone(),
                                now)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessDto getBusiness(UUID businessId) {
        BusinessAccountEntity business = businessRepository.findVisibleById(businessId)
                .orElseThrow(() -> new BusinessNotFoundException(businessId));

        List<BusinessHoursEntity> week = hoursRepository.findByBusinessIdOrderByWeekdayAsc(businessId);

        // cities.id is a slug ("cluj-napoca"), not something to show a user — the display name
        // ("Cluj-Napoca") lives in cities.name. A city id that no longer resolves leaves cityName
        // null rather than failing the whole page.
        String cityName = cityRepository.findById(business.getCityId())
                .map(CityEntity::getName)
                .orElse(null);

        return new BusinessDto(
                business.getId(),
                business.getName(),
                business.getType().getId(),
                business.getType().getType(),
                business.getDescription(),
                resolveLogoUrl(business.getLogoUrl()),
                business.getAddress(),
                business.getCityId(),
                cityName,
                GeoSupport.latOf(business.getLocation()),
                GeoSupport.lngOf(business.getLocation()),
                business.getPhoneNumber(),
                business.getEmail(),
                business.getWebsiteUrl(),
                toRating(business.getAverageRating()),
                business.getReviewCount(),
                business.getFollowerCount(),
                business.getTimezone(),
                OpeningHours.isOpenAt(week, business.getTimezone(), Instant.now()),
                week.stream().map(BusinessServiceImpl::toHoursDto).toList(),
                business.getVerifiedAt(),
                business.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessTypeOptionDto> listTypes() {
        return typeRepository.findAllByOrderByTypeAsc()
                .stream()
                .map(BusinessTypeOptionEntity::toDto)
                .sorted(Comparator.comparing(BusinessTypeOptionDto::label))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessRefDto> findBusinessRefsByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return businessRepository.findVisibleByIds(ids)
                .stream()
                .map(business -> new BusinessRefDto(
                        business.getId(),
                        business.getName(),
                        resolveLogoUrl(business.getLogoUrl())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessRefDto> searchByName(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return businessRepository.searchVisibleByNameStartingWith(prefix.trim(), PageRequest.of(0, 20))
                .stream()
                .map(business -> new BusinessRefDto(
                        business.getId(),
                        business.getName(),
                        resolveLogoUrl(business.getLogoUrl())))
                .toList();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Rejects search parameters that are meaningless or abusive before they reach Postgres. NaN and
     * infinity are checked explicitly: they pass ordinary range comparisons and would reach
     * {@code ST_MakePoint} as garbage.
     */
    private void validateSearchArea(double lat, double lng, double radiusKm, int limit) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
            throw new InvalidSearchAreaException("lat must be a number between -90 and 90");
        }
        if (!Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw new InvalidSearchAreaException("lng must be a number between -180 and 180");
        }
        if (!Double.isFinite(radiusKm) || radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new InvalidSearchAreaException("radius_km must be greater than 0 and at most " + MAX_RADIUS_KM);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidSearchAreaException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    /**
     * {@code business_accounts.logo_url} stores an R2 <em>object key</em>, not a URL — the client
     * cannot render it as-is. Resolved to the public URL of the {@code BUSINESS} bucket here, the
     * same way {@code ProfileDtoMapper} resolves avatar keys. An already-absolute URL is passed
     * through untouched, so a logo hosted elsewhere still works.
     */
    private String resolveLogoUrl(String stored) {
        if (stored == null || stored.isBlank() || stored.startsWith("http")) {
            return stored;
        }
        return storageService.publicUrl(StorageBucket.BUSINESS, stored);
    }

    private static BusinessHoursDto toHoursDto(BusinessHoursEntity hours) {
        return new BusinessHoursDto(
                hours.getWeekday(),
                hours.isClosed(),
                format(hours.getOpeningHour()),
                format(hours.getClosingHour()),
                hours.getNotes());
    }

    /** Times are wall-clock with no date, so they go over the wire as {@code "HH:mm"} strings. */
    private static String format(LocalTime time) {
        return time == null ? null : time.format(HH_MM);
    }

    private static double toRating(BigDecimal rating) {
        return rating == null ? 0.0 : rating.doubleValue();
    }
}
