package com.tweakdapp.backend.business.internal;

import com.tweakdapp.backend.business.dto.AdminBusinessDto;
import com.tweakdapp.backend.business.dto.AdminBusinessPageDto;
import com.tweakdapp.backend.business.exception.BusinessNotFoundException;
import com.tweakdapp.backend.business.exception.InvalidBusinessCursorException;
import com.tweakdapp.backend.business.exception.InvalidBusinessStatusException;
import com.tweakdapp.backend.business.internal.entities.BusinessAccountEntity;
import com.tweakdapp.backend.business.internal.entities.BusinessTypeOptionEntity;
import com.tweakdapp.backend.business.internal.repositories.BusinessAccountRepository;
import com.tweakdapp.backend.business.internal.repositories.BusinessHoursRepository;
import com.tweakdapp.backend.business.internal.repositories.BusinessTypeOptionRepository;
import com.tweakdapp.backend.shared.geo.CityRepository;
import com.tweakdapp.backend.shared.geo.GeoSupport;
import com.tweakdapp.backend.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The verification decisions themselves. These are the only writes the business module has, and
 * they decide whether a company appears in the app at all, so the rules are pinned here rather than
 * left to the controller: a rejection always carries a reason, a re-verification does not leave the
 * old objection behind it, and {@code deleted} is not something a dashboard button can do.
 */
class BusinessServiceImplReviewTest {

    private static final UUID BUSINESS_ID = UUID.fromString("967d6b34-7b61-4d8a-a7df-8e670c79d680");
    private static final UUID STAFF_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private BusinessAccountRepository businessRepository;
    private BusinessHoursRepository hoursRepository;
    private CityRepository cityRepository;
    private BusinessServiceImpl service;

    @BeforeEach
    void setUp() {
        businessRepository = mock(BusinessAccountRepository.class);
        hoursRepository = mock(BusinessHoursRepository.class);
        cityRepository = mock(CityRepository.class);
        service = new BusinessServiceImpl(
                businessRepository,
                hoursRepository,
                mock(BusinessTypeOptionRepository.class),
                cityRepository,
                mock(StorageService.class));

        when(hoursRepository.findByBusinessIdOrderByWeekdayAsc(any())).thenReturn(List.of());
        when(cityRepository.findById(any())).thenReturn(Optional.empty());
        when(businessRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---- listing ------------------------------------------------------------

    @Test
    void theQueueAsksForOneRowMoreThanThePageSizeAndHandsBackACursor() {
        List<BusinessAccountEntity> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rows.add(pending(UUID.randomUUID(), Instant.parse("2026-09-0" + (i + 1) + "T10:00:00Z")));
        }
        when(businessRepository.findForReview(eq("pending"), isNull(), isNull(), isNull(), any(Limit.class)))
                .thenReturn(rows);

        AdminBusinessPageDto page = service.listForReview("pending", null, null, 2);

        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(businessRepository).findForReview(eq("pending"), isNull(), isNull(), isNull(), limit.capture());
        assertThat(limit.getValue().max()).isEqualTo(3);

        // The probe row is never returned, and its predecessor is what the cursor points at.
        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotNull();
    }

    @Test
    void theLastPageHasNoCursor() {
        when(businessRepository.findForReview(any(), any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of(pending(BUSINESS_ID, Instant.parse("2026-09-01T10:00:00Z"))));

        assertThat(service.listForReview(null, null, null, 20).nextCursor()).isNull();
    }

    @Test
    void aBlankStatusFilterMeansNoFilterRatherThanAnError() {
        when(businessRepository.findForReview(isNull(), isNull(), isNull(), isNull(), any(Limit.class)))
                .thenReturn(List.of());

        service.listForReview("  ", "", null, 20);

        verify(businessRepository).findForReview(isNull(), isNull(), isNull(), isNull(), any(Limit.class));
    }

    @Test
    void anUnknownStatusIsRejectedRatherThanReturningAnEmptyQueue() {
        // A typo'd filter that silently returned nothing would read as "nothing to review".
        assertThatThrownBy(() -> service.listForReview("pendign", null, null, 20))
                .isInstanceOf(InvalidBusinessStatusException.class);
        assertThatThrownBy(() -> service.listForReview(null, "archived", null, 20))
                .isInstanceOf(InvalidBusinessStatusException.class);
    }

    @Test
    void anUnparseableCursorIsABadRequest() {
        assertThatThrownBy(() -> service.listForReview(null, null, "not-a-cursor!!", 20))
                .isInstanceOf(InvalidBusinessCursorException.class);
    }

    @Test
    void thePageSizeIsClamped() {
        when(businessRepository.findForReview(any(), any(), any(), any(), any(Limit.class)))
                .thenReturn(List.of());

        service.listForReview(null, null, null, 10_000);

        ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);
        verify(businessRepository).findForReview(any(), any(), any(), any(), limit.capture());
        assertThat(limit.getValue().max()).isEqualTo(101);
    }

    // ---- decisions ----------------------------------------------------------

    @Test
    void verifyingStampsTheDecisionAndClearsAnyEarlierRejection() {
        BusinessAccountEntity business = pending(BUSINESS_ID, Instant.parse("2026-09-01T10:00:00Z"));
        business.setVerificationStatus(BusinessAccountEntity.REJECTED);
        business.setRejectionReason("Address does not exist");
        when(businessRepository.findForReviewById(BUSINESS_ID)).thenReturn(Optional.of(business));

        AdminBusinessDto dto = service.verify(BUSINESS_ID, STAFF_ID);

        assertThat(dto.verificationStatus()).isEqualTo("verified");
        assertThat(dto.rejectionReason()).isNull();
        assertThat(dto.verifiedAt()).isNotNull();
        assertThat(dto.reviewedBy()).isEqualTo(STAFF_ID);
        assertThat(dto.reviewedAt()).isNotNull();
    }

    @Test
    void reVerifyingKeepsTheOriginalVerificationDate() {
        Instant first = Instant.parse("2026-08-01T10:00:00Z");
        BusinessAccountEntity business = pending(BUSINESS_ID, first);
        business.setVerifiedAt(first);
        when(businessRepository.findForReviewById(BUSINESS_ID)).thenReturn(Optional.of(business));

        assertThat(service.verify(BUSINESS_ID, STAFF_ID).verifiedAt()).isEqualTo(first);
    }

    @Test
    void rejectingWithoutAReasonIsRefused() {
        when(businessRepository.findForReviewById(BUSINESS_ID))
                .thenReturn(Optional.of(pending(BUSINESS_ID, Instant.now())));

        assertThatThrownBy(() -> service.reject(BUSINESS_ID, "   ", STAFF_ID))
                .isInstanceOf(InvalidBusinessStatusException.class);
        assertThatThrownBy(() -> service.reject(BUSINESS_ID, null, STAFF_ID))
                .isInstanceOf(InvalidBusinessStatusException.class);
    }

    @Test
    void anOverlongRejectionReasonIsRefused() {
        assertThatThrownBy(() -> service.reject(BUSINESS_ID, "x".repeat(501), STAFF_ID))
                .isInstanceOf(InvalidBusinessStatusException.class);
    }

    @Test
    void rejectingRecordsTheTrimmedReasonAndLeavesVerifiedAtAlone() {
        Instant verifiedAt = Instant.parse("2026-08-01T10:00:00Z");
        BusinessAccountEntity business = pending(BUSINESS_ID, verifiedAt);
        business.setVerifiedAt(verifiedAt);
        when(businessRepository.findForReviewById(BUSINESS_ID)).thenReturn(Optional.of(business));

        AdminBusinessDto dto = service.reject(BUSINESS_ID, "  Not a real address  ", STAFF_ID);

        assertThat(dto.verificationStatus()).isEqualTo("rejected");
        assertThat(dto.rejectionReason()).isEqualTo("Not a real address");
        assertThat(dto.verifiedAt()).isEqualTo(verifiedAt);
        assertThat(dto.reviewedBy()).isEqualTo(STAFF_ID);
    }

    @Test
    void suspendingLeavesVerificationUntouched() {
        BusinessAccountEntity business = pending(BUSINESS_ID, Instant.now());
        business.setVerificationStatus(BusinessAccountEntity.VERIFIED);
        when(businessRepository.findForReviewById(BUSINESS_ID)).thenReturn(Optional.of(business));

        AdminBusinessDto dto = service.setActiveStatus(BUSINESS_ID, "suspended", STAFF_ID);

        assertThat(dto.activeStatus()).isEqualTo("suspended");
        assertThat(dto.verificationStatus()).isEqualTo("verified");
    }

    @Test
    void deletingIsNotADashboardAction() {
        assertThatThrownBy(() -> service.setActiveStatus(BUSINESS_ID, "deleted", STAFF_ID))
                .isInstanceOf(InvalidBusinessStatusException.class)
                .hasMessageContaining("deleting a business is not a dashboard action");
    }

    @Test
    void anUnknownBusinessIsANotFound() {
        when(businessRepository.findForReviewById(BUSINESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(BUSINESS_ID, STAFF_ID))
                .isInstanceOf(BusinessNotFoundException.class);
        assertThatThrownBy(() -> service.getForReview(BUSINESS_ID))
                .isInstanceOf(BusinessNotFoundException.class);
    }

    // ------------------------------------------------------------------

    private static BusinessAccountEntity pending(UUID id, Instant createdAt) {
        BusinessTypeOptionEntity type = new BusinessTypeOptionEntity();
        type.setId("tire_shop");
        type.setType("Tire shop");

        BusinessAccountEntity business = new BusinessAccountEntity();
        business.setId(id);
        business.setName("Willy Wash");
        business.setType(type);
        business.setCityId("cluj-napoca");
        business.setAddress("Str. Aviator 12");
        business.setLocation(GeoSupport.point(46.7874479284246, 23.6308604799099));
        business.setTimezone("Europe/Bucharest");
        business.setAverageRating(new BigDecimal("5.0"));
        business.setVerificationStatus(BusinessAccountEntity.PENDING);
        business.setActiveStatus(BusinessAccountEntity.ACTIVE);
        business.setCreatedAt(createdAt);
        return business;
    }
}
