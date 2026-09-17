package com.tweakdapp.backend.support.internal;

import com.tweakdapp.backend.support.SupportService;
import com.tweakdapp.backend.support.dto.CreateTicketRequest;
import com.tweakdapp.backend.support.dto.TicketCategoryDto;
import com.tweakdapp.backend.support.dto.TicketDto;
import com.tweakdapp.backend.support.dto.TicketMessageRequest;
import com.tweakdapp.backend.support.dto.TicketPageDto;
import com.tweakdapp.backend.shared.ratelimit.RateLimited;
import com.tweakdapp.backend.shared.ratelimit.RateLimits;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The app-side support endpoints: open a ticket, follow the conversation, reply. Everything is
 * scoped to the JWT subject. The staff endpoints live in the {@code admin} module.
 */
@RestController
@RequestMapping("/api/v1/support")
class SupportController {

    private final SupportService supportService;

    SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    /** The categories a user may pick when opening a ticket. */
    @GetMapping("/categories")
    public List<TicketCategoryDto> getCategories() {
        return supportService.listCategories();
    }

    /** Opens a ticket with its first message. */
    @PostMapping("/tickets")
    @RateLimited(RateLimits.FEEDBACK_CREATE)
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDto createTicket(@AuthenticationPrincipal Jwt jwt,
                                  @Valid @RequestBody CreateTicketRequest request) {
        return supportService.createTicket(UUID.fromString(jwt.getSubject()), request);
    }

    /** The caller's own tickets, most recently active first. */
    @GetMapping("/tickets/mine")
    public TicketPageDto getMyTickets(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(defaultValue = "20") int size) {
        return supportService.listMyTickets(UUID.fromString(jwt.getSubject()), cursor, size);
    }

    /** One of the caller's tickets with its full conversation. */
    @GetMapping("/tickets/{ticketId}")
    public TicketDto getTicket(@AuthenticationPrincipal Jwt jwt,
                               @PathVariable UUID ticketId) {
        return supportService.getTicket(UUID.fromString(jwt.getSubject()), ticketId);
    }

    /** Replies inside the caller's own ticket (reopens it if it was resolved). */
    @PostMapping("/tickets/{ticketId}/messages")
    @RateLimited(RateLimits.COMMENTS)
    public TicketDto addMessage(@AuthenticationPrincipal Jwt jwt,
                                @PathVariable UUID ticketId,
                                @Valid @RequestBody TicketMessageRequest request) {
        return supportService.addMessage(UUID.fromString(jwt.getSubject()), ticketId, request);
    }
}
