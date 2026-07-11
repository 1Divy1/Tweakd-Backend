package com.carsocialmedia.backend.admin.internal.controllers;

import com.carsocialmedia.backend.admin.internal.AdminAccessService;
import com.carsocialmedia.backend.admin.internal.AdminSupportService;
import com.carsocialmedia.backend.admin.internal.Capability;
import com.carsocialmedia.backend.support.SupportService;
import com.carsocialmedia.backend.support.dto.AdminTicketUpdateRequest;
import com.carsocialmedia.backend.support.dto.TicketDto;
import com.carsocialmedia.backend.support.dto.TicketMessageRequest;
import com.carsocialmedia.backend.support.dto.TicketPageDto;
import com.carsocialmedia.backend.support.dto.TicketStatsDto;
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

import java.util.UUID;

/**
 * The Support Tickets page. Queue reads and triage updates delegate straight to the support
 * module; the staff reply goes through {@link AdminSupportService} so the requester gets an in-app
 * notification.
 */
@RestController
@RequestMapping("/api/v1/admin/support")
class AdminSupportController {

    private final AdminAccessService access;
    private final SupportService supportService;
    private final AdminSupportService adminSupportService;

    AdminSupportController(AdminAccessService access,
                           SupportService supportService,
                           AdminSupportService adminSupportService) {
        this.access = access;
        this.supportService = supportService;
        this.adminSupportService = adminSupportService;
    }

    /** One keyset page of the ticket queue, most recently active first. */
    @GetMapping("/tickets")
    public TicketPageDto listTickets(@AuthenticationPrincipal Jwt jwt,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "20") int size) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.ANSWER_TICKETS);
        return supportService.listTickets(status, cursor, size);
    }

    @GetMapping("/tickets/stats")
    public TicketStatsDto getStats(@AuthenticationPrincipal Jwt jwt) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.ANSWER_TICKETS);
        return supportService.getStats();
    }

    @GetMapping("/tickets/{ticketId}")
    public TicketDto getTicket(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID ticketId) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.ANSWER_TICKETS);
        return supportService.getTicketAsStaff(ticketId);
    }

    /** Staff reply: ticket moves to {@code awaiting_user}, the requester is notified in-app. */
    @PostMapping("/tickets/{ticketId}/messages")
    public TicketDto reply(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID ticketId,
                           @Valid @RequestBody TicketMessageRequest request) {
        UUID staffId = UUID.fromString(jwt.getSubject());
        access.require(staffId, Capability.ANSWER_TICKETS);
        return adminSupportService.reply(staffId, ticketId, request);
    }

    /** Triage: priority, status, assignee — any non-null field of the request is applied. */
    @PatchMapping("/tickets/{ticketId}")
    public TicketDto updateTicket(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID ticketId,
                                  @Valid @RequestBody AdminTicketUpdateRequest request) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.ANSWER_TICKETS);
        return supportService.updateTicket(ticketId, request);
    }
}
