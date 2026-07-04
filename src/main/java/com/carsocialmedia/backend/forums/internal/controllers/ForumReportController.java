package com.carsocialmedia.backend.forums.internal.controllers;

import com.carsocialmedia.backend.forums.ForumsService;
import com.carsocialmedia.backend.report.dto.ReportReasonDto;
import com.carsocialmedia.backend.report.dto.ReportRequest;
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
import java.util.UUID;

/**
 * Reporting endpoints for forum threads and replies. They live in the {@code forums} module — next
 * to the resource being reported — and delegate persistence to the {@code report} module: thread /
 * reply existence and self-report checks run in {@link ForumsService}, while the preset reason lists
 * are a straight passthrough to the report module.
 */
@RestController
@RequestMapping("/api/v1/forums")
public class ForumReportController {

    private final ForumsService forumsService;

    public ForumReportController(ForumsService forumsService) {
        this.forumsService = forumsService;
    }

    /** The preset reasons a user may pick from when reporting a thread. */
    @GetMapping("/threads/report-reasons")
    public List<ReportReasonDto> getThreadReportReasons() {
        return forumsService.listThreadReportReasons();
    }

    /** The preset reasons a user may pick from when reporting a reply. */
    @GetMapping("/replies/report-reasons")
    public List<ReportReasonDto> getReplyReportReasons() {
        return forumsService.listReplyReportReasons();
    }

    /** Files a report against a thread. The body (and its {@code reason_id}) is optional. */
    @PostMapping("/threads/{threadId}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportThread(@AuthenticationPrincipal Jwt jwt,
                             @PathVariable UUID threadId,
                             @RequestBody(required = false) ReportRequest request) {
        forumsService.reportThread(jwt.getSubject(), threadId, request == null ? null : request.reasonId());
    }

    /** Files a report against a reply. The body (and its {@code reason_id}) is optional. */
    @PostMapping("/replies/{replyId}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportReply(@AuthenticationPrincipal Jwt jwt,
                            @PathVariable UUID replyId,
                            @RequestBody(required = false) ReportRequest request) {
        forumsService.reportReply(jwt.getSubject(), replyId, request == null ? null : request.reasonId());
    }
}
