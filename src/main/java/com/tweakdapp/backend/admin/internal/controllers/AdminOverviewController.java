package com.tweakdapp.backend.admin.internal.controllers;

import com.tweakdapp.backend.admin.internal.AdminAccessService;
import com.tweakdapp.backend.admin.internal.AdminOverviewService;
import com.tweakdapp.backend.admin.internal.dto.OverviewDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The dashboard's landing page. Gated on team membership only (not VIEW_ANALYTICS) — every team
 * member lands here; the capability is reserved for deeper analytics later.
 */
@RestController
@RequestMapping("/api/v1/admin/overview")
class AdminOverviewController {

    private final AdminAccessService access;
    private final AdminOverviewService overviewService;

    AdminOverviewController(AdminAccessService access, AdminOverviewService overviewService) {
        this.access = access;
        this.overviewService = overviewService;
    }

    @GetMapping
    public OverviewDto getOverview(@AuthenticationPrincipal Jwt jwt) {
        access.requireMember(UUID.fromString(jwt.getSubject()));
        return overviewService.getOverview();
    }
}
