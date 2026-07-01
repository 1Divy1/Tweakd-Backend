package com.carsocialmedia.backend.report.internal;

import com.carsocialmedia.backend.report.ReportService;
import com.carsocialmedia.backend.report.dto.MyReportDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The only REST endpoint the {@code report} module owns: a user's read-only view of the reports
 * <em>they</em> filed.
 *
 * <p>Unlike filing a report (which validates the target and so lives in the {@code posts} /
 * {@code profile} modules to avoid a Modulith cycle), listing a reporter's own reports is a pure
 * read of the report tables — it needs no lookup into other modules, so it can live here without
 * creating a cycle. The listing is always scoped to the JWT subject, so a user can only ever see
 * their own reports, never anyone else's.
 */
@RestController
@RequestMapping("/api/v1/reports")
class MyReportsController {

    private final ReportService reportService;

    MyReportsController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** All reports (post, comment, and profile) filed by the authenticated user, newest first. */
    @GetMapping("/mine")
    public List<MyReportDto> getMyReports(@AuthenticationPrincipal Jwt jwt) {
        return reportService.listMyReports(UUID.fromString(jwt.getSubject()));
    }
}
