package com.tweakdapp.backend.admin.internal;

import com.tweakdapp.backend.notification.NotificationService;
import com.tweakdapp.backend.support.SupportService;
import com.tweakdapp.backend.support.dto.TicketDto;
import com.tweakdapp.backend.support.dto.TicketMessageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Bridges staff ticket replies to the requester's in-app notifications — the support module
 * deliberately does not depend on {@code notification}, so the fan-out lives here. Queue reads and
 * triage updates pass straight through to {@link SupportService} from the controller.
 */
@Service
public class AdminSupportService {

    private final SupportService supportService;
    private final NotificationService notificationService;

    AdminSupportService(SupportService supportService, NotificationService notificationService) {
        this.supportService = supportService;
        this.notificationService = notificationService;
    }

    /** Adds the staff reply (ticket → {@code awaiting_user}) and notifies the requester in-app. */
    @Transactional
    public TicketDto reply(java.util.UUID staffId, java.util.UUID ticketId, TicketMessageRequest request) {
        TicketDto ticket = supportService.addStaffMessage(staffId, ticketId, request);
        notificationService.push(
                ticket.requester().id(),
                "ticket_reply",
                "Support replied to your ticket",
                "\"" + ticket.subject() + "\" has a new reply from our team.",
                Map.of("ticket_id", ticket.id().toString()));
        return ticket;
    }
}
