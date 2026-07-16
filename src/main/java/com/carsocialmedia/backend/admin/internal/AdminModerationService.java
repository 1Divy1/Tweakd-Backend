package com.carsocialmedia.backend.admin.internal;

import com.carsocialmedia.backend.admin.exception.CaseNotFoundException;
import com.carsocialmedia.backend.admin.exception.InvalidModerationActionException;
import com.carsocialmedia.backend.admin.internal.dto.CaseDetailDto;
import com.carsocialmedia.backend.admin.internal.dto.CaseDetailDto.CaseActionDto;
import com.carsocialmedia.backend.admin.internal.dto.CaseDetailDto.CaseReportDto;
import com.carsocialmedia.backend.admin.internal.dto.CasePageDto;
import com.carsocialmedia.backend.admin.internal.dto.CaseSummaryDto;
import com.carsocialmedia.backend.admin.internal.dto.QueueCountsDto;
import com.carsocialmedia.backend.admin.internal.entities.ModerationActionEntity;
import com.carsocialmedia.backend.admin.internal.entities.ModerationCaseEntity;
import com.carsocialmedia.backend.admin.internal.repositories.ModerationActionRepository;
import com.carsocialmedia.backend.admin.internal.repositories.ModerationCaseRepository;
import com.carsocialmedia.backend.forums.ForumsService;
import com.carsocialmedia.backend.notification.NotificationService;
import com.carsocialmedia.backend.posts.PostsService;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileModerationSnapshotDto;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.carsocialmedia.backend.report.ReportService;
import com.carsocialmedia.backend.report.dto.TargetReportDto;
import com.carsocialmedia.backend.shared.moderation.ModerationContentDto;
import com.carsocialmedia.backend.shared.staff.StaffDirectory;
import com.carsocialmedia.backend.shared.staff.StaffRefDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The Content Moderation page: the case queue, the case detail, and the moderator decisions. A
 * decision always follows the same shape — write the audit row ({@code moderation_actions}), close
 * out the underlying reports, then apply the effect (delete content / warn / ban) and resolve the
 * case. For a content removal the audit row is written <em>before</em> the hard delete, because the
 * report rows CASCADE away with the content and the snapshot is the only surviving record.
 */
@Service
public class AdminModerationService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 140;

    /** Reason fragments that mark a report severe regardless of report count (see report_reasons). */
    private static final Set<String> SEVERE_REASON_FRAGMENTS = Set.of(
            "violence", "hate", "suicide", "self-injury", "harassment", "illegal");

    private static final Set<String> TARGET_TYPES = Set.of(
            "post", "comment", "profile", "forum_thread", "forum_thread_reply");

    private final ModerationCaseRepository caseRepository;
    private final ModerationActionRepository actionRepository;
    private final ReportService reportService;
    private final PostsService postsService;
    private final ForumsService forumsService;
    private final ProfileService profileService;
    private final NotificationService notificationService;
    private final StaffDirectory staffDirectory;

    AdminModerationService(ModerationCaseRepository caseRepository,
                           ModerationActionRepository actionRepository,
                           ReportService reportService,
                           PostsService postsService,
                           ForumsService forumsService,
                           ProfileService profileService,
                           NotificationService notificationService,
                           StaffDirectory staffDirectory) {
        this.caseRepository = caseRepository;
        this.actionRepository = actionRepository;
        this.reportService = reportService;
        this.postsService = postsService;
        this.forumsService = forumsService;
        this.profileService = profileService;
        this.notificationService = notificationService;
        this.staffDirectory = staffDirectory;
    }

    // -------------------------------------------------------------------
    // QUEUE
    // -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CasePageDto getQueue(String status, String type, String cursorToken, int size) {
        List<String> statuses = statusFilter(status);
        if (type != null && !TARGET_TYPES.contains(type)) {
            throw new InvalidModerationActionException("Unknown target type filter: " + type);
        }
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);

        CaseCursor cursor = CaseCursor.decode(cursorToken);
        List<ModerationCaseEntity> page = caseRepository.findQueue(
                statuses, type,
                cursor == null ? null : cursor.lastReportedAt(),
                cursor == null ? 0L : cursor.id(),
                PageRequest.of(0, pageSize + 1));

        boolean hasMore = page.size() > pageSize;
        if (hasMore) {
            page = page.subList(0, pageSize);
        }
        String nextCursor = hasMore
                ? new CaseCursor(page.getLast().getLastReportedAt(), page.getLast().getId()).encode()
                : null;
        return new CasePageDto(toSummaries(page), nextCursor);
    }

    /** The overview's "newest reports" card. */
    @Transactional(readOnly = true)
    public List<CaseSummaryDto> latestPendingCases(int limit) {
        return toSummaries(caseRepository.findQueue(
                List.of("open", "escalated"), null, null, 0L, PageRequest.of(0, limit)));
    }

    @Transactional(readOnly = true)
    public QueueCountsDto getQueueCounts() {
        Map<String, Long> pendingByType = caseRepository.countPendingByType().stream()
                .collect(Collectors.toMap(
                        ModerationCaseRepository.KeyCount::getKey,
                        ModerationCaseRepository.KeyCount::getCount));
        Map<String, Long> byStatus = caseRepository.countByStatus().stream()
                .collect(Collectors.toMap(
                        ModerationCaseRepository.KeyCount::getKey,
                        ModerationCaseRepository.KeyCount::getCount));
        return new QueueCountsDto(pendingByType, byStatus);
    }

    // -------------------------------------------------------------------
    // CASE DETAIL
    // -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CaseDetailDto getCase(long caseId) {
        ModerationCaseEntity caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));

        List<TargetReportDto> reports = reportService.listReportsForTarget(
                caseEntity.getTargetType(), caseEntity.getTargetId());
        ModerationContentDto content = snapshotOrNull(caseEntity.getTargetType(), caseEntity.getTargetId());
        List<ModerationActionEntity> actions =
                actionRepository.findByCaseIdOrderByCreatedAtAsc(caseEntity.getId());

        // Reporters are app users (one profile batch); moderators and the resolver are staff.
        Set<UUID> profileIds = new HashSet<>();
        reports.forEach(r -> profileIds.add(r.reporterId()));
        Map<UUID, ProfileSearchResultDto> profiles = profilesById(profileIds);

        Set<UUID> staffIds = new HashSet<>();
        actions.forEach(a -> {
            if (a.getModeratorId() != null) {
                staffIds.add(a.getModeratorId());
            }
        });
        if (caseEntity.getResolvedBy() != null) {
            staffIds.add(caseEntity.getResolvedBy());
        }
        Map<UUID, StaffRefDto> staff = staffDirectory.findByIds(staffIds);

        ProfileModerationSnapshotDto author = content == null ? null
                : moderationSnapshotOrNull(content.authorId());

        return new CaseDetailDto(
                caseEntity.getId(),
                caseEntity.getTargetType(),
                caseEntity.getTargetId(),
                caseEntity.getStatus(),
                caseEntity.getResolution(),
                severity(reports),
                content == null ? null : content.content(),
                content == null ? null : content.mediaUrls(),
                content == null ? null : content.createdAt(),
                author,
                reports.stream()
                        .map(r -> new CaseReportDto(profiles.get(r.reporterId()), r.reason(), r.status(), r.createdAt()))
                        .toList(),
                actions.stream()
                        .map(a -> new CaseActionDto(a.getAction(),
                                a.getModeratorId() == null ? null : staff.get(a.getModeratorId()),
                                a.getNote(), a.getCreatedAt()))
                        .toList(),
                caseEntity.getCreatedAt(),
                caseEntity.getLastReportedAt(),
                caseEntity.getResolvedAt(),
                caseEntity.getResolvedBy() == null ? null : staff.get(caseEntity.getResolvedBy()));
    }

    // -------------------------------------------------------------------
    // DECISIONS
    // -------------------------------------------------------------------

    /** Content is fine: dismiss the reports, keep everything else untouched. */
    @Transactional
    public void approve(UUID moderatorId, long caseId, String note) {
        ModerationCaseEntity caseEntity = loadPendingCase(caseId);
        ModerationContentDto content = snapshotOrNull(caseEntity.getTargetType(), caseEntity.getTargetId());
        reportService.updateReportsStatusForTarget(
                caseEntity.getTargetType(), caseEntity.getTargetId(), "dismissed");
        recordAction(moderatorId, caseEntity, "approve",
                content == null ? null : content.authorId(), null, note);
        resolve(caseEntity, "approved", moderatorId);
    }

    /**
     * Hard-deletes the reported content through its own module (comments and replied-to forum
     * content soft-delete by those modules' rules) and notifies the author. The audit snapshot is
     * written first — the report rows CASCADE away with the content.
     */
    @Transactional
    public void removeContent(UUID moderatorId, long caseId, String note) {
        ModerationCaseEntity caseEntity = loadPendingCase(caseId);
        if ("profile".equals(caseEntity.getTargetType())) {
            throw new InvalidModerationActionException(
                    "A profile has no content to remove — warn or ban the user instead");
        }

        ModerationContentDto content = snapshotOrNull(caseEntity.getTargetType(), caseEntity.getTargetId());
        reportService.updateReportsStatusForTarget(
                caseEntity.getTargetType(), caseEntity.getTargetId(), "resolved");
        recordAction(moderatorId, caseEntity, "remove_content",
                content == null ? null : content.authorId(),
                content == null ? null : content.content(), note);

        if (content != null) {
            deleteContent(caseEntity.getTargetType(), caseEntity.getTargetId());
            notificationService.push(content.authorId(), "content_removed",
                    "Your content was removed",
                    note != null && !note.isBlank() ? note : "It was found to break our community guidelines.",
                    Map.of("targetType", caseEntity.getTargetType(), "caseId", caseEntity.getId()));
        }
        resolve(caseEntity, "content_removed", moderatorId);
    }

    /** Keeps the content but sends the author a formal in-app warning. */
    @Transactional
    public void warn(UUID moderatorId, long caseId, String note) {
        if (note == null || note.isBlank()) {
            throw new InvalidModerationActionException("A warning message is required");
        }
        ModerationCaseEntity caseEntity = loadPendingCase(caseId);
        UUID authorId = requireAuthor(caseEntity);

        reportService.updateReportsStatusForTarget(
                caseEntity.getTargetType(), caseEntity.getTargetId(), "resolved");
        recordAction(moderatorId, caseEntity, "warn", authorId, null, note);
        notificationService.push(authorId, "moderation_warning",
                "You have received a warning", note,
                Map.of("targetType", caseEntity.getTargetType(), "caseId", caseEntity.getId()));
        resolve(caseEntity, "author_warned", moderatorId);
    }

    /**
     * Bans the author (temp when {@code banDays} is set, permanent otherwise). Enforcement is the
     * profile module's banned-user interceptor; the content itself stays unless removed separately.
     */
    @Transactional
    public void ban(UUID moderatorId, long caseId, String note, Integer banDays) {
        ModerationCaseEntity caseEntity = loadPendingCase(caseId);
        UUID authorId = requireAuthor(caseEntity);

        Instant until = banDays == null ? null : Instant.now().plus(banDays, ChronoUnit.DAYS);
        profileService.banUser(authorId, until);
        reportService.updateReportsStatusForTarget(
                caseEntity.getTargetType(), caseEntity.getTargetId(), "resolved");
        recordAction(moderatorId, caseEntity, "ban", authorId, null, note);
        resolve(caseEntity, "author_banned", moderatorId);
    }

    /** Flags the case for a senior admin; it stays in the queue under the {@code escalated} status. */
    @Transactional
    public void escalate(UUID moderatorId, long caseId, String note) {
        ModerationCaseEntity caseEntity = loadPendingCase(caseId);
        ModerationContentDto content = snapshotOrNull(caseEntity.getTargetType(), caseEntity.getTargetId());
        recordAction(moderatorId, caseEntity, "escalate",
                content == null ? null : content.authorId(), null, note);
        caseEntity.setStatus("escalated");
        caseRepository.save(caseEntity);
    }

    /** Lifts a ban outside any case context (the author panel's unban button). */
    @Transactional
    public void unban(UUID moderatorId, UUID profileId, String note) {
        profileService.unbanUser(profileId);
        ModerationActionEntity action = new ModerationActionEntity();
        action.setId(UUID.randomUUID());
        action.setModeratorId(moderatorId);
        action.setAction("unban");
        action.setTargetType("profile");
        action.setTargetId(profileId);
        action.setTargetAuthorId(profileId);
        action.setNote(note);
        actionRepository.save(action);
    }

    // -------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------

    private List<String> statusFilter(String status) {
        if (status == null || status.isBlank()) {
            return List.of("open", "escalated"); // the default queue: everything needing attention
        }
        return switch (status) {
            case "open", "escalated", "resolved" -> List.of(status);
            default -> throw new InvalidModerationActionException("Unknown status filter: " + status);
        };
    }

    private List<CaseSummaryDto> toSummaries(List<ModerationCaseEntity> cases) {
        if (cases.isEmpty()) {
            return List.of();
        }
        // Per-case snapshot + report lookups fan out across modules; fine at admin-page sizes.
        Map<Long, ModerationContentDto> contentByCase = new HashMap<>();
        Map<Long, List<TargetReportDto>> reportsByCase = new HashMap<>();
        for (ModerationCaseEntity c : cases) {
            contentByCase.put(c.getId(), snapshotOrNull(c.getTargetType(), c.getTargetId()));
            reportsByCase.put(c.getId(), reportService.listReportsForTarget(c.getTargetType(), c.getTargetId()));
        }
        Map<UUID, ProfileSearchResultDto> authors = profilesById(contentByCase.values().stream()
                .filter(content -> content != null)
                .map(ModerationContentDto::authorId)
                .collect(Collectors.toSet()));

        return cases.stream()
                .map(c -> {
                    ModerationContentDto content = contentByCase.get(c.getId());
                    List<TargetReportDto> reports = reportsByCase.get(c.getId());
                    return new CaseSummaryDto(
                            c.getId(),
                            c.getTargetType(),
                            c.getTargetId(),
                            c.getStatus(),
                            severity(reports),
                            reports.size(),
                            content == null ? "[deleted]" : preview(content.content()),
                            content == null ? null : authors.get(content.authorId()),
                            c.getCreatedAt(),
                            c.getLastReportedAt());
                })
                .toList();
    }

    /**
     * LOW / MEDIUM / HIGH from report volume and reason gravity: five reports (or any severe
     * reason) is HIGH, more than one is MEDIUM, a single ordinary report is LOW.
     */
    private String severity(List<TargetReportDto> reports) {
        boolean severeReason = reports.stream()
                .map(TargetReportDto::reason)
                .filter(reason -> reason != null)
                .map(reason -> reason.toLowerCase(Locale.ROOT))
                .anyMatch(reason -> SEVERE_REASON_FRAGMENTS.stream().anyMatch(reason::contains));
        if (reports.size() >= 5 || severeReason) {
            return "HIGH";
        }
        return reports.size() >= 2 ? "MEDIUM" : "LOW";
    }

    /**
     * The reported thing in the uniform shape, or {@code null} if it vanished while the case was
     * open (author self-delete). A reported profile maps to the same shape: the "content" is the
     * handle, the author is the profile itself.
     */
    private ModerationContentDto snapshotOrNull(String targetType, UUID targetId) {
        return switch (targetType) {
            case "post" -> postsService.findPostModerationSnapshot(targetId).orElse(null);
            case "comment" -> postsService.findCommentModerationSnapshot(targetId).orElse(null);
            case "forum_thread" -> forumsService.findThreadModerationSnapshot(targetId).orElse(null);
            case "forum_thread_reply" -> forumsService.findReplyModerationSnapshot(targetId).orElse(null);
            case "profile" -> profileService.findModerationSnapshot(targetId)
                    .map(profile -> new ModerationContentDto(targetId, targetId, "@" + profile.username(),
                            List.of(), profile.createdAt()))
                    .orElse(null);
            default -> throw new IllegalStateException("Unknown moderation target type: " + targetType);
        };
    }

    private ProfileModerationSnapshotDto moderationSnapshotOrNull(UUID profileId) {
        return profileService.findModerationSnapshot(profileId).orElse(null);
    }

    private void deleteContent(String targetType, UUID targetId) {
        switch (targetType) {
            case "post" -> postsService.deletePostAsModerator(targetId);
            case "comment" -> postsService.deleteCommentAsModerator(targetId);
            case "forum_thread" -> forumsService.deleteThreadAsModerator(targetId);
            case "forum_thread_reply" -> forumsService.deleteReplyAsModerator(targetId);
            default -> throw new IllegalStateException("Not deletable: " + targetType);
        }
    }

    private ModerationCaseEntity loadPendingCase(long caseId) {
        ModerationCaseEntity caseEntity = caseRepository.findById(caseId)
                .orElseThrow(() -> new CaseNotFoundException(caseId));
        if ("resolved".equals(caseEntity.getStatus())) {
            throw new InvalidModerationActionException(
                    "Case #" + caseId + " is already resolved (" + caseEntity.getResolution() + ")");
        }
        return caseEntity;
    }

    /** The target's author, required by warn / ban — 400 when the content (and author) is gone. */
    private UUID requireAuthor(ModerationCaseEntity caseEntity) {
        ModerationContentDto content = snapshotOrNull(caseEntity.getTargetType(), caseEntity.getTargetId());
        if (content == null) {
            throw new InvalidModerationActionException(
                    "The reported content no longer exists, so its author cannot be resolved");
        }
        return content.authorId();
    }

    private void recordAction(UUID moderatorId, ModerationCaseEntity caseEntity, String actionName,
                              UUID targetAuthorId, String contentSnapshot, String note) {
        ModerationActionEntity action = new ModerationActionEntity();
        action.setId(UUID.randomUUID());
        action.setCaseId(caseEntity.getId());
        action.setModeratorId(moderatorId);
        action.setAction(actionName);
        action.setTargetType(caseEntity.getTargetType());
        action.setTargetId(caseEntity.getTargetId());
        action.setTargetAuthorId(targetAuthorId);
        action.setContentSnapshot(contentSnapshot);
        action.setNote(note == null || note.isBlank() ? null : note);
        actionRepository.save(action);
    }

    private void resolve(ModerationCaseEntity caseEntity, String resolution, UUID moderatorId) {
        caseEntity.setStatus("resolved");
        caseEntity.setResolution(resolution);
        caseEntity.setResolvedBy(moderatorId);
        caseEntity.setResolvedAt(Instant.now());
        caseRepository.save(caseEntity);
    }

    private Map<UUID, ProfileSearchResultDto> profilesById(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return profileService.findByIds(ids).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        String flat = content.replace('\n', ' ').strip();
        return flat.length() <= PREVIEW_LENGTH ? flat : flat.substring(0, PREVIEW_LENGTH) + "…";
    }
}
