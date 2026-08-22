package com.tweakdapp.backend.support.internal;

import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.support.SupportService;
import com.tweakdapp.backend.support.dto.AdminTicketUpdateRequest;
import com.tweakdapp.backend.support.dto.CreateTicketRequest;
import com.tweakdapp.backend.support.dto.TicketCategoryDto;
import com.tweakdapp.backend.support.dto.TicketDto;
import com.tweakdapp.backend.support.dto.TicketMessageDto;
import com.tweakdapp.backend.support.dto.TicketMessageRequest;
import com.tweakdapp.backend.support.dto.TicketPageDto;
import com.tweakdapp.backend.support.dto.TicketStatsDto;
import com.tweakdapp.backend.support.dto.TicketSummaryDto;
import com.tweakdapp.backend.support.exception.InvalidAssigneeException;
import com.tweakdapp.backend.support.exception.InvalidTicketCategoryException;
import com.tweakdapp.backend.support.exception.InvalidTicketPriorityException;
import com.tweakdapp.backend.support.exception.InvalidTicketStatusException;
import com.tweakdapp.backend.support.exception.TicketNotFoundException;
import com.tweakdapp.backend.support.internal.entities.SupportTicketEntity;
import com.tweakdapp.backend.support.internal.entities.SupportTicketMessageEntity;
import com.tweakdapp.backend.shared.staff.StaffDirectory;
import com.tweakdapp.backend.shared.staff.StaffRefDto;
import com.tweakdapp.backend.support.internal.repositories.SupportTicketMessageRepository;
import com.tweakdapp.backend.support.internal.repositories.SupportTicketRepository;
import com.tweakdapp.backend.support.internal.repositories.TicketCategoryOptionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SupportServiceImpl implements SupportService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 120;
    private static final Set<String> PRIORITIES = Set.of("low", "normal", "high", "urgent");
    private static final Set<String> STATUSES = Set.of("open", "awaiting_user", "resolved");

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final TicketCategoryOptionRepository categoryRepository;
    private final ProfileService profileService;
    private final StaffDirectory staffDirectory;

    @PersistenceContext
    private EntityManager entityManager;

    public SupportServiceImpl(SupportTicketRepository ticketRepository,
                              SupportTicketMessageRepository messageRepository,
                              TicketCategoryOptionRepository categoryRepository,
                              ProfileService profileService,
                              StaffDirectory staffDirectory) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.categoryRepository = categoryRepository;
        this.profileService = profileService;
        this.staffDirectory = staffDirectory;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketCategoryDto> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(option -> new TicketCategoryDto(option.getId(), option.getName()))
                .toList();
    }

    @Override
    @Transactional
    public TicketDto createTicket(UUID userId, CreateTicketRequest request) {
        if (!categoryRepository.existsById(request.category())) {
            throw new InvalidTicketCategoryException(request.category());
        }

        SupportTicketEntity ticket = new SupportTicketEntity();
        ticket.setId(UUID.randomUUID());
        ticket.setUserId(userId);
        ticket.setSubject(request.subject().strip());
        ticket.setCategory(request.category());
        ticketRepository.save(ticket);

        SupportTicketMessageEntity message = new SupportTicketMessageEntity();
        message.setId(UUID.randomUUID());
        message.setTicketId(ticket.getId());
        message.setSenderId(userId);
        message.setStaff(false);
        message.setContent(request.message().strip());
        messageRepository.save(message);

        // Flush + clear so the re-read below picks up the DB-managed columns (created_at, the
        // DEFAULT status/priority, and the trigger-set last_message_at).
        entityManager.flush();
        entityManager.clear();
        return getTicketAsStaff(ticket.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public TicketPageDto listMyTickets(UUID userId, String cursor, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        TicketCursor decoded = cursor == null ? null : TicketCursor.decode(cursor);
        List<SupportTicketEntity> rows = ticketRepository.findMine(
                userId,
                decoded == null ? null : decoded.lastMessageAt(),
                decoded == null ? null : decoded.id(),
                PageRequest.of(0, pageSize + 1));
        return toPage(rows, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketDto getTicket(UUID requesterId, UUID ticketId) {
        SupportTicketEntity ticket = ticketRepository.findById(ticketId)
                .filter(t -> t.getUserId().equals(requesterId))
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        return toDto(ticket);
    }

    @Override
    @Transactional
    public TicketDto addMessage(UUID userId, UUID ticketId, TicketMessageRequest request) {
        SupportTicketEntity ticket = ticketRepository.findById(ticketId)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        return appendMessage(ticket, userId, false, request.content());
    }

    // ------------------------------------------------------------------
    // Staff side
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public TicketPageDto listTickets(String status, String cursor, int size) {
        if (status != null && !STATUSES.contains(status)) {
            throw new InvalidTicketStatusException(status);
        }
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        TicketCursor decoded = cursor == null ? null : TicketCursor.decode(cursor);
        List<SupportTicketEntity> rows = ticketRepository.findQueue(
                status,
                decoded == null ? null : decoded.lastMessageAt(),
                decoded == null ? null : decoded.id(),
                PageRequest.of(0, pageSize + 1));
        return toPage(rows, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketDto getTicketAsStaff(UUID ticketId) {
        SupportTicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        return toDto(ticket);
    }

    @Override
    @Transactional
    public TicketDto addStaffMessage(UUID staffId, UUID ticketId, TicketMessageRequest request) {
        SupportTicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));
        return appendMessage(ticket, staffId, true, request.content());
    }

    @Override
    @Transactional
    public TicketDto updateTicket(UUID ticketId, AdminTicketUpdateRequest request) {
        SupportTicketEntity ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new TicketNotFoundException(ticketId));

        if (request.priority() != null) {
            if (!PRIORITIES.contains(request.priority())) {
                throw new InvalidTicketPriorityException(request.priority());
            }
            ticket.setPriority(request.priority());
        }
        if (request.status() != null) {
            if (!STATUSES.contains(request.status())) {
                throw new InvalidTicketStatusException(request.status());
            }
            ticket.setStatus(request.status());
            ticket.setResolvedAt("resolved".equals(request.status()) ? Instant.now() : null);
        }
        if (Boolean.TRUE.equals(request.unassign())) {
            ticket.setAssignedTo(null);
        } else if (request.assigneeId() != null) {
            // The FK to profiles is gone (staff aren't app users), so team membership is the check.
            if (!staffDirectory.findByIds(Set.of(request.assigneeId())).containsKey(request.assigneeId())) {
                throw new InvalidAssigneeException(request.assigneeId());
            }
            ticket.setAssignedTo(request.assigneeId());
        }

        return toDto(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketStatsDto getStats() {
        Instant midnightUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new TicketStatsDto(
                ticketRepository.countByStatus("open"),
                ticketRepository.countByStatus("awaiting_user"),
                ticketRepository.countByResolvedAtAfter(midnightUtc));
    }

    // ------------------------------------------------------------------
    // Assembly
    // ------------------------------------------------------------------

    /** Appends one message, applies the status flip the sender implies, and returns the fresh ticket. */
    private TicketDto appendMessage(SupportTicketEntity ticket, UUID senderId, boolean staff, String content) {
        SupportTicketMessageEntity message = new SupportTicketMessageEntity();
        message.setId(UUID.randomUUID());
        message.setTicketId(ticket.getId());
        message.setSenderId(senderId);
        message.setStaff(staff);
        message.setContent(content.strip());
        messageRepository.save(message);

        // A staff reply hands the ball to the user; a user reply (re)joins the staff queue.
        ticket.setStatus(staff ? "awaiting_user" : "open");
        ticket.setResolvedAt(null);

        // Flush + clear so the re-read picks up the trigger-bumped last_message_at.
        UUID ticketId = ticket.getId();
        entityManager.flush();
        entityManager.clear();
        return getTicketAsStaff(ticketId);
    }

    private TicketDto toDto(SupportTicketEntity ticket) {
        List<SupportTicketMessageEntity> messages =
                messageRepository.findByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());

        // Two identity pools: requester + user messages are profiles; the assignee and staff
        // message senders are dashboard staff, who have no profiles row.
        Set<UUID> profileIds = new HashSet<>();
        profileIds.add(ticket.getUserId());
        Set<UUID> staffIds = new HashSet<>();
        if (ticket.getAssignedTo() != null) {
            staffIds.add(ticket.getAssignedTo());
        }
        messages.forEach(m -> (m.isStaff() ? staffIds : profileIds).add(m.getSenderId()));
        Map<UUID, ProfileSearchResultDto> profiles = profilesByIds(profileIds);
        Map<UUID, StaffRefDto> staff = staffDirectory.findByIds(staffIds);
        boolean business = profileService.findBusinessProfileIds(Set.of(ticket.getUserId()))
                .contains(ticket.getUserId());

        List<TicketMessageDto> messageDtos = messages.stream()
                .map(m -> new TicketMessageDto(
                        m.getId(),
                        m.isStaff() ? null : profiles.get(m.getSenderId()),
                        m.isStaff() ? staff.get(m.getSenderId()) : null,
                        m.isStaff(),
                        m.getContent(),
                        m.getCreatedAt()))
                .toList();

        return new TicketDto(
                ticket.getId(),
                ticket.getSubject(),
                ticket.getCategory(),
                ticket.getPriority(),
                ticket.getStatus(),
                profiles.get(ticket.getUserId()),
                business,
                ticket.getAssignedTo() == null ? null : staff.get(ticket.getAssignedTo()),
                messageDtos,
                ticket.getCreatedAt(),
                ticket.getLastMessageAt());
    }

    /** Turns a size + 1 keyset load into a summary page with one profile / preview batch each. */
    private TicketPageDto toPage(List<SupportTicketEntity> rows, int pageSize) {
        boolean hasMore = rows.size() > pageSize;
        List<SupportTicketEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
                ? new TicketCursor(page.getLast().getLastMessageAt(), page.getLast().getId()).encode()
                : null;

        if (page.isEmpty()) {
            return new TicketPageDto(List.of(), null);
        }

        List<UUID> ticketIds = page.stream().map(SupportTicketEntity::getId).toList();
        Set<UUID> requesterIds = page.stream().map(SupportTicketEntity::getUserId).collect(Collectors.toSet());
        Map<UUID, ProfileSearchResultDto> requesters = profilesByIds(requesterIds);
        Set<UUID> businessIds = Set.copyOf(profileService.findBusinessProfileIds(requesterIds));
        Map<UUID, String> previews = messageRepository.findLatestPerTicket(ticketIds).stream()
                .collect(Collectors.toMap(
                        SupportTicketMessageRepository.PreviewRow::getTicketId,
                        row -> truncate(row.getContent())));

        List<TicketSummaryDto> items = page.stream()
                .map(t -> new TicketSummaryDto(
                        t.getId(),
                        t.getSubject(),
                        t.getCategory(),
                        t.getPriority(),
                        t.getStatus(),
                        requesters.get(t.getUserId()),
                        businessIds.contains(t.getUserId()),
                        previews.get(t.getId()),
                        t.getCreatedAt(),
                        t.getLastMessageAt()))
                .toList();
        return new TicketPageDto(items, nextCursor);
    }

    private Map<UUID, ProfileSearchResultDto> profilesByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return profileService.findByIds(ids).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));
    }

    private String truncate(String content) {
        return content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "…";
    }
}
