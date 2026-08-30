package com.tweakdapp.backend.support;

import com.tweakdapp.backend.support.dto.AdminTicketUpdateRequest;
import com.tweakdapp.backend.support.dto.CreateTicketRequest;
import com.tweakdapp.backend.support.dto.TicketCategoryDto;
import com.tweakdapp.backend.support.dto.TicketDto;
import com.tweakdapp.backend.support.dto.TicketMessageRequest;
import com.tweakdapp.backend.support.dto.TicketPageDto;
import com.tweakdapp.backend.support.dto.TicketStatsDto;

import java.util.List;
import java.util.UUID;

/**
 * Support tickets from users and business accounts: the app-side conversation (create / reply /
 * track) and the staff-side operations the admin dashboard needs (queue, reply, triage). The module
 * owns {@code support_tickets}, {@code support_ticket_messages}, and the seeded
 * {@code support_ticket_categories}.
 *
 * <p>Status model: {@code open} = waiting on staff, {@code awaiting_user} = staff replied last,
 * {@code resolved} = closed. A user reply always moves the ticket (back) to {@code open}; a staff
 * reply moves it to {@code awaiting_user}. Priority is staff-set only.
 *
 * <p>The staff methods do <b>no</b> authorization — the {@code admin} module gates them behind its
 * team-capability checks before delegating here.
 */
public interface SupportService {

    /** The categories a user may pick when opening a ticket, in display order. */
    List<TicketCategoryDto> listCategories();

    /**
     * Opens a ticket with its first message.
     *
     * @throws com.tweakdapp.backend.support.exception.InvalidTicketCategoryException if
     *         {@code category} doesn't reference a {@code support_ticket_categories} row
     */
    TicketDto createTicket(UUID userId, CreateTicketRequest request);

    /**
     * One keyset page of the caller's own tickets, most recently active first.
     *
     * @throws com.tweakdapp.backend.support.exception.InvalidTicketCursorException if the
     *         cursor cannot be decoded
     */
    TicketPageDto listMyTickets(UUID userId, String cursor, int size);

    /**
     * One ticket with its full conversation — only the requester may load it; someone else's ticket
     * is deliberately an indistinguishable 404.
     *
     * @throws com.tweakdapp.backend.support.exception.TicketNotFoundException if the ticket
     *         does not exist or belongs to another user
     */
    TicketDto getTicket(UUID requesterId, UUID ticketId);

    /**
     * Adds a user reply to their own ticket and moves it (back) to {@code open} — replying to a
     * resolved ticket reopens it.
     *
     * @throws com.tweakdapp.backend.support.exception.TicketNotFoundException if the ticket
     *         does not exist or belongs to another user
     */
    TicketDto addMessage(UUID userId, UUID ticketId, TicketMessageRequest request);

    // ------------------------------------------------------------------
    // Staff side (called by the admin module)
    // ------------------------------------------------------------------

    /**
     * One keyset page of the staff queue, most recently active first.
     *
     * @param status optional filter ({@code open} / {@code awaiting_user} / {@code resolved}),
     *        {@code null} = all
     */
    TicketPageDto listTickets(String status, String cursor, int size);

    /**
     * Any ticket with its full conversation, without the ownership check.
     *
     * @throws com.tweakdapp.backend.support.exception.TicketNotFoundException if it doesn't exist
     */
    TicketDto getTicketAsStaff(UUID ticketId);

    /**
     * Adds a staff reply and moves the ticket to {@code awaiting_user}. Returns the updated ticket
     * (the caller notifies the requester — this module does not depend on {@code notification}).
     *
     * @throws com.tweakdapp.backend.support.exception.TicketNotFoundException if it doesn't exist
     */
    TicketDto addStaffMessage(UUID staffId, UUID ticketId, TicketMessageRequest request);

    /**
     * Applies a staff triage update — any non-null field of the request: priority, status,
     * assignee. The assignee's team membership is the caller's concern.
     *
     * @throws com.tweakdapp.backend.support.exception.TicketNotFoundException if it doesn't exist
     * @throws com.tweakdapp.backend.support.exception.InvalidTicketPriorityException if
     *         {@code priority} isn't low / normal / high / urgent
     * @throws com.tweakdapp.backend.support.exception.InvalidTicketStatusException if
     *         {@code status} isn't open / awaiting_user / resolved
     */
    TicketDto updateTicket(UUID ticketId, AdminTicketUpdateRequest request);

    /** Headline numbers for the dashboard's Tickets page. */
    TicketStatsDto getStats();
}
