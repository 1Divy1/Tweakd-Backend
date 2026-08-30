package com.tweakdapp.backend.profile.internal.controller;

import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.report.ReportService;
import com.tweakdapp.backend.report.dto.ReportReasonDto;
import com.tweakdapp.backend.report.dto.ReportRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reporting endpoints for profiles. They live in the {@code profile} module — next to the resource
 * being reported — and delegate the actual persistence to the {@code report} module: username
 * resolution and the self-report check run in {@link ProfileService}, while the preset reason list
 * is a straight passthrough to {@link ReportService}.
 */
@RestController
@RequestMapping("/api/v1/profile")
class ProfileReportController {

    private final ProfileService profileService;
    private final ReportService reportService;

    ProfileReportController(ProfileService profileService, ReportService reportService) {
        this.profileService = profileService;
        this.reportService = reportService;
    }

    /** The preset reasons a user may pick from when reporting a profile. */
    @GetMapping("/report-reasons")
    public List<ReportReasonDto> getProfileReportReasons() {
        return reportService.listProfileReportReasons();
    }

    /** Files a report against the given user's profile. The body (and its {@code reasonId}) is optional. */
    @PostMapping("/{username}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportProfile(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable String username,
                              @RequestBody(required = false) ReportRequest request) {
        profileService.reportProfile(jwt.getSubject(), username, request == null ? null : request.reasonId());
    }
}
