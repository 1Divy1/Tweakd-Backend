package com.tweakdapp.backend.admin.internal.controllers;

import com.tweakdapp.backend.admin.internal.AdminAccessService;
import com.tweakdapp.backend.admin.internal.Capability;
import com.tweakdapp.backend.admin.internal.dto.BusinessActiveStatusRequest;
import com.tweakdapp.backend.admin.internal.dto.BusinessRejectionRequest;
import com.tweakdapp.backend.business.BusinessService;
import com.tweakdapp.backend.business.dto.AdminBusinessDto;
import com.tweakdapp.backend.business.dto.AdminBusinessPageDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * The Businesses review page: a business is invisible to the app until someone verifies it, and
 * until now that flip was a hand-written UPDATE in Supabase. Every endpoint needs
 * {@code VERIFY_BUSINESSES}, which only {@code owner} and {@code senior_admin} hold — putting a
 * real company on the map is a publishing decision with commercial weight, not a moderation one.
 *
 * <p>There is deliberately no create, edit or delete here. The {@code business} module stays
 * read-only for everything except these status decisions; a business is still authored elsewhere.
 */
@RestController
@RequestMapping("/api/v1/admin/businesses")
class AdminBusinessesController {

    private final AdminAccessService access;
    private final BusinessService businessService;

    AdminBusinessesController(AdminAccessService access, BusinessService businessService) {
        this.access = access;
        this.businessService = businessService;
    }

    /**
     * One keyset page of the review queue, <strong>oldest submission first</strong>. Both filters
     * are optional and independent: {@code verification_status} ({@code pending} / {@code verified}
     * / {@code rejected}) and {@code active_status} ({@code active} / {@code suspended} /
     * {@code deleted}). Omit both to see everything.
     */
    @GetMapping
    public AdminBusinessPageDto list(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(name = "verification_status", required = false)
                                     String verificationStatus,
                                     @RequestParam(name = "active_status", required = false)
                                     String activeStatus,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "20") int size) {
        access.require(userId(jwt), Capability.VERIFY_BUSINESSES);
        return businessService.listForReview(verificationStatus, activeStatus, cursor, size);
    }

    /** How many businesses are waiting for verification — the dashboard badge. */
    @GetMapping("/counts")
    public Map<String, Long> counts(@AuthenticationPrincipal Jwt jwt) {
        access.require(userId(jwt), Capability.VERIFY_BUSINESSES);
        return Map.of("pending", businessService.countPendingVerification());
    }

    /** Full detail of one business, whatever its status. */
    @GetMapping("/{businessId}")
    public AdminBusinessDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId) {
        access.require(userId(jwt), Capability.VERIFY_BUSINESSES);
        return businessService.getForReview(businessId);
    }

    /**
     * Approves the business, putting it on the map if its active status allows. Idempotent, and
     * clears any earlier rejection reason.
     */
    @PostMapping("/{businessId}/verify")
    public AdminBusinessDto verify(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId) {
        UUID staffId = userId(jwt);
        access.require(staffId, Capability.VERIFY_BUSINESSES);
        return businessService.verify(businessId, staffId);
    }

    /** Turns the business down with a reason. Nothing is deleted; re-verifying later is one call. */
    @PostMapping("/{businessId}/reject")
    public AdminBusinessDto reject(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable UUID businessId,
                                   @Valid @RequestBody BusinessRejectionRequest request) {
        UUID staffId = userId(jwt);
        access.require(staffId, Capability.VERIFY_BUSINESSES);
        return businessService.reject(businessId, request.reason(), staffId);
    }

    /** Suspends a business (off the map, verification untouched) or reactivates it. */
    @PatchMapping("/{businessId}/active-status")
    public AdminBusinessDto setActiveStatus(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID businessId,
                                            @Valid @RequestBody BusinessActiveStatusRequest request) {
        UUID staffId = userId(jwt);
        access.require(staffId, Capability.VERIFY_BUSINESSES);
        return businessService.setActiveStatus(businessId, request.activeStatus(), staffId);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
