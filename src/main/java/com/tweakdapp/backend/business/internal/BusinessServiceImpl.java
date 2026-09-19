package com.tweakdapp.backend.business.internal;

import com.tweakdapp.backend.business.BusinessService;
import com.tweakdapp.backend.business.dto.AdminBusinessDto;
import com.tweakdapp.backend.business.dto.AdminBusinessPageDto;
import com.tweakdapp.backend.business.dto.AdminBusinessSummaryDto;
import com.tweakdapp.backend.business.dto.BusinessDto;
import com.tweakdapp.backend.business.dto.BusinessHoursDto;
import com.tweakdapp.backend.business.dto.BusinessMapPinDto;
import com.tweakdapp.backend.business.dto.BusinessRefDto;
import com.tweakdapp.backend.business.dto.BusinessSearchPageDto;
import com.tweakdapp.backend.business.dto.BusinessTypeOptionDto;
import com.tweakdapp.backend.business.exception.BusinessNotFoundException;
import com.tweakdapp.backend.business.exception.InvalidBusinessStatusException;
import com.tweakdapp.backend.business.exception.InvalidSearchAreaException;
import com.tweakdapp.backend.business.internal.entities.BusinessAccountEntity;
import com.tweakdapp.backend.business.internal.entities.BusinessHoursEntity;
import com.tweakdapp.backend.business.internal.entities.BusinessTypeOptionEntity;
import com.tweakdapp.backend.business.internal.repositories.BusinessAccountRepository;
import com.tweakdapp.backend.business.internal.repositories.BusinessHoursRepository;
import com.tweakdapp.backend.business.internal.repositories.BusinessTypeOptionRepository;
import com.tweakdapp.backend.shared.geo.CityEntity;
import com.tweakdapp.backend.shared.geo.CityRepository;
import com.tweakdapp.backend.shared.geo.GeoSupport;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import org.springframework.data.domain.Limit;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    /** Shorter queries match nearly everything and tell the user nothing. */
    private static final int MIN_SEARCH_LENGTH = 2;

    /** Longer than any real business name; trimmed rather than refused. */
    private static final int MAX_SEARCH_LENGTH = 100;

    /** Ceiling on a search page, so a hand-crafted {@code size} cannot pull the table. */
    private static final int MAX_SEARCH_PAGE_SIZE = 50;

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    /** Ceiling on a review-queue page, so a hand-crafted {@code size} cannot pull the table. */
    private static final int MAX_REVIEW_PAGE_SIZE = 100;

    /** Long enough to explain a refusal, short enough that it cannot be used as free storage. */
    private static final int MAX_REJECTION_REASON = 500;

    private static final Set<String> VERIFICATION_STATUSES =
            Set.of(BusinessAccountEntity.PENDING, BusinessAccountEntity.VERIFIED, BusinessAccountEntity.REJECTED);

    private static final Set<String> ACTIVE_STATUSES =
            Set.of(BusinessAccountEntity.ACTIVE, BusinessAccountEntity.SUSPENDED, BusinessAccountEntity.DELETED);

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

        return toPins(businessRepository.findVisibleNearby(lat, lng, radiusKm * 1000.0, normalisedType, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessSearchPageDto search(String query, double lat, double lng, String cursor, int size) {
        validateCentre(lat, lng);
        BusinessSearchCursor after = BusinessSearchCursor.decode(cursor);

        String text = query == null ? "" : query.strip();
        if (text.length() < MIN_SEARCH_LENGTH) {
            return new BusinessSearchPageDto(List.of(), null);
        }
        if (text.length() > MAX_SEARCH_LENGTH) {
            text = text.substring(0, MAX_SEARCH_LENGTH);
        }
        int pageSize = Math.clamp(size, 1, MAX_SEARCH_PAGE_SIZE);

        List<BusinessAccountRepository.SearchPinRow> rows = businessRepository.searchVisible(
                containsPattern(text), lat, lng, after.distanceMetres(), after.id(), pageSize + 1);

        // One row over the page size is the "is there more" probe; it is never returned.
        boolean hasMore = rows.size() > pageSize;
        List<BusinessAccountRepository.SearchPinRow> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
                ? new BusinessSearchCursor(page.getLast().getDistanceMetres(), page.getLast().getId()).encode()
                : null;

        return new BusinessSearchPageDto(toPins(page), nextCursor);
    }

    /** Assembles rows into map pins, with one batched hours lookup for the whole list. */
    private List<BusinessMapPinDto> toPins(List<? extends BusinessAccountRepository.MapPinRow> rows) {
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
    // Admin review
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AdminBusinessPageDto listForReview(String verificationStatus, String activeStatus,
                                              String cursor, int size) {
        String verification = normaliseVerificationFilter(verificationStatus);
        String active = normaliseActiveFilter(activeStatus);
        int pageSize = Math.clamp(size, 1, MAX_REVIEW_PAGE_SIZE);
        BusinessCursor decoded = BusinessCursor.decode(cursor);

        List<BusinessAccountEntity> rows = businessRepository.findForReview(
                verification,
                active,
                decoded == null ? null : decoded.timestamp(),
                decoded == null ? null : decoded.id(),
                Limit.of(pageSize + 1));

        // One row over the page size is the "is there more" probe; it is never returned.
        boolean hasMore = rows.size() > pageSize;
        List<BusinessAccountEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
                ? new BusinessCursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode()
                : null;

        return new AdminBusinessPageDto(page.stream().map(this::toSummaryDto).toList(), nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminBusinessDto getForReview(UUID businessId) {
        return toAdminDto(loadForReview(businessId));
    }

    @Override
    @Transactional(readOnly = true)
    public long countPendingVerification() {
        return businessRepository.countByVerificationStatus(BusinessAccountEntity.PENDING);
    }

    @Override
    @Transactional
    public AdminBusinessDto verify(UUID businessId, UUID reviewerId) {
        BusinessAccountEntity business = loadForReview(businessId);
        Instant now = Instant.now();
        business.setVerificationStatus(BusinessAccountEntity.VERIFIED);
        // Cleared, not kept: a stale reason next to a verified business reads as a live objection.
        business.setRejectionReason(null);
        if (business.getVerifiedAt() == null) {
            business.setVerifiedAt(now);
        }
        stampReview(business, reviewerId, now);
        return toAdminDto(businessRepository.save(business));
    }

    @Override
    @Transactional
    public AdminBusinessDto reject(UUID businessId, String reason, UUID reviewerId) {
        String trimmed = reason == null ? "" : reason.strip();
        if (trimmed.isEmpty()) {
            throw new InvalidBusinessStatusException("A rejection reason is required");
        }
        if (trimmed.length() > MAX_REJECTION_REASON) {
            throw new InvalidBusinessStatusException(
                    "A rejection reason cannot exceed " + MAX_REJECTION_REASON + " characters");
        }

        BusinessAccountEntity business = loadForReview(businessId);
        business.setVerificationStatus(BusinessAccountEntity.REJECTED);
        business.setRejectionReason(trimmed);
        // verified_at is left as it was: it records that an approval once happened, and a later
        // rejection does not un-happen it.
        stampReview(business, reviewerId, Instant.now());
        return toAdminDto(businessRepository.save(business));
    }

    @Override
    @Transactional
    public AdminBusinessDto setActiveStatus(UUID businessId, String activeStatus, UUID reviewerId) {
        String status = activeStatus == null ? "" : activeStatus.strip();
        if (!BusinessAccountEntity.ACTIVE.equals(status) && !BusinessAccountEntity.SUSPENDED.equals(status)) {
            throw new InvalidBusinessStatusException(
                    "active_status must be 'active' or 'suspended'; deleting a business is not a dashboard action");
        }

        BusinessAccountEntity business = loadForReview(businessId);
        business.setActiveStatus(status);
        stampReview(business, reviewerId, Instant.now());
        return toAdminDto(businessRepository.save(business));
    }

    private BusinessAccountEntity loadForReview(UUID businessId) {
        return businessRepository.findForReviewById(businessId)
                .orElseThrow(() -> new BusinessNotFoundException(businessId));
    }

    private static void stampReview(BusinessAccountEntity business, UUID reviewerId, Instant now) {
        business.setReviewedBy(reviewerId);
        business.setReviewedAt(now);
    }

    private String normaliseVerificationFilter(String status) {
        return normaliseFilter(status, VERIFICATION_STATUSES, "verification_status");
    }

    private String normaliseActiveFilter(String status) {
        return normaliseFilter(status, ACTIVE_STATUSES, "active_status");
    }

    /**
     * Blank means "no filter"; anything else must be a known value. Rejecting an unknown status
     * rather than passing it through matters for more than tidiness: a typo would otherwise return
     * an empty queue, which a reviewer reads as "nothing to do".
     */
    private static String normaliseFilter(String status, Set<String> allowed, String field) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalised = status.strip().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalised)) {
            throw new InvalidBusinessStatusException(
                    field + " must be one of " + allowed + " (got: " + status + ")");
        }
        return normalised;
    }

    private AdminBusinessSummaryDto toSummaryDto(BusinessAccountEntity business) {
        return new AdminBusinessSummaryDto(
                business.getId(),
                business.getName(),
                business.getType().getId(),
                business.getType().getType(),
                resolveLogoUrl(business.getLogoUrl()),
                business.getAddress(),
                business.getCityId(),
                business.getVerificationStatus(),
                business.getActiveStatus(),
                business.getRejectionReason(),
                business.getVerifiedAt(),
                business.getCreatedAt());
    }

    private AdminBusinessDto toAdminDto(BusinessAccountEntity business) {
        List<BusinessHoursEntity> week = hoursRepository.findByBusinessIdOrderByWeekdayAsc(business.getId());
        String cityName = cityRepository.findById(business.getCityId())
                .map(CityEntity::getName)
                .orElse(null);

        return new AdminBusinessDto(
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
                business.getVerificationStatus(),
                business.getActiveStatus(),
                business.getRejectionReason(),
                business.getVerifiedAt(),
                business.getReviewedBy(),
                business.getReviewedAt(),
                business.getCreatedAt());
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
        validateCentre(lat, lng);
        if (!Double.isFinite(radiusKm) || radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new InvalidSearchAreaException("radius_km must be greater than 0 and at most " + MAX_RADIUS_KM);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidSearchAreaException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private static void validateCentre(double lat, double lng) {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
            throw new InvalidSearchAreaException("lat must be a number between -90 and 90");
        }
        if (!Double.isFinite(lng) || lng < -180 || lng > 180) {
            throw new InvalidSearchAreaException("lng must be a number between -180 and 180");
        }
    }

    /**
     * {@code %text%} for an {@code ILIKE ... ESCAPE '\'}, with the user's own wildcard characters
     * escaped so they match literally — otherwise a search for {@code "_"} would match every name.
     */
    static String containsPattern(String text) {
        String escaped = text
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
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
