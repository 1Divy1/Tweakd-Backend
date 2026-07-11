package com.carsocialmedia.backend.report.internal;

import com.carsocialmedia.backend.report.ReportService;
import com.carsocialmedia.backend.report.dto.MyReportDto;
import com.carsocialmedia.backend.report.dto.ReportReasonDto;
import com.carsocialmedia.backend.report.dto.TargetReportDto;
import com.carsocialmedia.backend.report.exception.DuplicateReportException;
import com.carsocialmedia.backend.report.exception.InvalidReportReasonException;
import com.carsocialmedia.backend.report.internal.entities.CommentReportEntity;
import com.carsocialmedia.backend.report.internal.entities.CommentReportId;
import com.carsocialmedia.backend.report.internal.entities.ForumThreadReplyReportEntity;
import com.carsocialmedia.backend.report.internal.entities.ForumThreadReplyReportId;
import com.carsocialmedia.backend.report.internal.entities.ForumThreadReportEntity;
import com.carsocialmedia.backend.report.internal.entities.ForumThreadReportId;
import com.carsocialmedia.backend.report.internal.entities.PostReportEntity;
import com.carsocialmedia.backend.report.internal.entities.PostReportId;
import com.carsocialmedia.backend.report.internal.entities.ProfileReportEntity;
import com.carsocialmedia.backend.report.internal.entities.ProfileReportId;
import com.carsocialmedia.backend.report.internal.entities.ReportReasonEntity;
import com.carsocialmedia.backend.report.internal.enums.ReportStatus;
import com.carsocialmedia.backend.report.internal.enums.ReportTarget;
import com.carsocialmedia.backend.report.internal.repositories.CommentReportRepository;
import com.carsocialmedia.backend.report.internal.repositories.ForumThreadReplyReportRepository;
import com.carsocialmedia.backend.report.internal.repositories.ForumThreadReportRepository;
import com.carsocialmedia.backend.report.internal.repositories.PostReportRepository;
import com.carsocialmedia.backend.report.internal.repositories.ProfileReportRepository;
import com.carsocialmedia.backend.report.internal.repositories.ReportReasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ReportServiceImpl implements ReportService {

    private final PostReportRepository postReportRepository;
    private final CommentReportRepository commentReportRepository;
    private final ProfileReportRepository profileReportRepository;
    private final ForumThreadReportRepository forumThreadReportRepository;
    private final ForumThreadReplyReportRepository forumThreadReplyReportRepository;
    private final ReportReasonRepository reportReasonRepository;

    public ReportServiceImpl(PostReportRepository postReportRepository,
                             CommentReportRepository commentReportRepository,
                             ProfileReportRepository profileReportRepository,
                             ForumThreadReportRepository forumThreadReportRepository,
                             ForumThreadReplyReportRepository forumThreadReplyReportRepository,
                             ReportReasonRepository reportReasonRepository) {
        this.postReportRepository = postReportRepository;
        this.commentReportRepository = commentReportRepository;
        this.profileReportRepository = profileReportRepository;
        this.forumThreadReportRepository = forumThreadReportRepository;
        this.forumThreadReplyReportRepository = forumThreadReplyReportRepository;
        this.reportReasonRepository = reportReasonRepository;
    }

    @Override
    @Transactional
    public void reportPost(UUID reporterId, UUID postId, UUID reasonId) {
        validateReason(reasonId, ReportTarget.post);
        if (postReportRepository.existsByIdPostIdAndIdReporterId(postId, reporterId)) {
            throw new DuplicateReportException();
        }
        PostReportEntity report = new PostReportEntity();
        report.setId(new PostReportId(postId, reporterId));
        report.setReasonId(reasonId);
        postReportRepository.save(report);
    }

    @Override
    @Transactional
    public void reportComment(UUID reporterId, UUID commentId, UUID reasonId) {
        validateReason(reasonId, ReportTarget.comment);
        if (commentReportRepository.existsByIdCommentIdAndIdReporterId(commentId, reporterId)) {
            throw new DuplicateReportException();
        }
        CommentReportEntity report = new CommentReportEntity();
        report.setId(new CommentReportId(commentId, reporterId));
        report.setReasonId(reasonId);
        commentReportRepository.save(report);
    }

    @Override
    @Transactional
    public void reportProfile(UUID reporterId, UUID profileId, UUID reasonId) {
        validateReason(reasonId, ReportTarget.profile);
        if (profileReportRepository.existsByIdProfileIdAndIdReporterId(profileId, reporterId)) {
            throw new DuplicateReportException();
        }
        ProfileReportEntity report = new ProfileReportEntity();
        report.setId(new ProfileReportId(profileId, reporterId));
        report.setReasonId(reasonId);
        profileReportRepository.save(report);
    }

    @Override
    @Transactional
    public void reportForumThread(UUID reporterId, UUID threadId, UUID reasonId) {
        validateReason(reasonId, ReportTarget.forum_thread);
        if (forumThreadReportRepository.existsByIdThreadIdAndIdReporterId(threadId, reporterId)) {
            throw new DuplicateReportException();
        }
        ForumThreadReportEntity report = new ForumThreadReportEntity();
        report.setId(new ForumThreadReportId(threadId, reporterId));
        report.setReasonId(reasonId);
        forumThreadReportRepository.save(report);
    }

    @Override
    @Transactional
    public void reportForumReply(UUID reporterId, UUID replyId, UUID reasonId) {
        validateReason(reasonId, ReportTarget.forum_thread_reply);
        if (forumThreadReplyReportRepository.existsByIdReplyIdAndIdReporterId(replyId, reporterId)) {
            throw new DuplicateReportException();
        }
        ForumThreadReplyReportEntity report = new ForumThreadReplyReportEntity();
        report.setId(new ForumThreadReplyReportId(replyId, reporterId));
        report.setReasonId(reasonId);
        forumThreadReplyReportRepository.save(report);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportReasonDto> listPostReportReasons() {
        return listReasons(ReportTarget.post);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportReasonDto> listCommentReportReasons() {
        return listReasons(ReportTarget.comment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportReasonDto> listProfileReportReasons() {
        return listReasons(ReportTarget.profile);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportReasonDto> listForumThreadReportReasons() {
        return listReasons(ReportTarget.forum_thread);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportReasonDto> listForumReplyReportReasons() {
        return listReasons(ReportTarget.forum_thread_reply);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyReportDto> listMyReports(UUID reporterId) {
        List<PostReportEntity> postReports = postReportRepository.findByIdReporterId(reporterId);
        List<CommentReportEntity> commentReports = commentReportRepository.findByIdReporterId(reporterId);
        List<ProfileReportEntity> profileReports = profileReportRepository.findByIdReporterId(reporterId);
        List<ForumThreadReportEntity> forumThreadReports = forumThreadReportRepository.findByIdReporterId(reporterId);
        List<ForumThreadReplyReportEntity> forumReplyReports = forumThreadReplyReportRepository.findByIdReporterId(reporterId);

        // Resolve all referenced reason texts in one batch to avoid an N+1 across the lists.
        List<UUID> reasonIds = Stream.of(
                        postReports.stream().map(PostReportEntity::getReasonId),
                        commentReports.stream().map(CommentReportEntity::getReasonId),
                        profileReports.stream().map(ProfileReportEntity::getReasonId),
                        forumThreadReports.stream().map(ForumThreadReportEntity::getReasonId),
                        forumReplyReports.stream().map(ForumThreadReplyReportEntity::getReasonId))
                .flatMap(Function.identity())
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, String> reasonText = reportReasonRepository.findAllById(reasonIds).stream()
                .collect(Collectors.toMap(ReportReasonEntity::getId, ReportReasonEntity::getReason));

        List<MyReportDto> reports = new ArrayList<>(postReports.size() + commentReports.size()
                + profileReports.size() + forumThreadReports.size() + forumReplyReports.size());
        for (PostReportEntity report : postReports) {
            reports.add(toDto(ReportTarget.post, report.getId().getPostId(), report.getReasonId(),
                    report.getStatus(), report.getCreatedAt(), reasonText));
        }
        for (CommentReportEntity report : commentReports) {
            reports.add(toDto(ReportTarget.comment, report.getId().getCommentId(), report.getReasonId(),
                    report.getStatus(), report.getCreatedAt(), reasonText));
        }
        for (ProfileReportEntity report : profileReports) {
            reports.add(toDto(ReportTarget.profile, report.getId().getProfileId(), report.getReasonId(),
                    report.getStatus(), report.getCreatedAt(), reasonText));
        }
        for (ForumThreadReportEntity report : forumThreadReports) {
            reports.add(toDto(ReportTarget.forum_thread, report.getId().getThreadId(), report.getReasonId(),
                    report.getStatus(), report.getCreatedAt(), reasonText));
        }
        for (ForumThreadReplyReportEntity report : forumReplyReports) {
            reports.add(toDto(ReportTarget.forum_thread_reply, report.getId().getReplyId(), report.getReasonId(),
                    report.getStatus(), report.getCreatedAt(), reasonText));
        }

        // Newest first across all three kinds.
        reports.sort(Comparator.comparing(MyReportDto::createdAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return reports;
    }

    // -------------------------------------------------------------------
    // MODERATION — called by the admin module (which does the auth)
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<TargetReportDto> listReportsForTarget(String targetType, UUID targetId) {
        List<TargetReportDto> reports = switch (ReportTarget.valueOf(targetType)) {
            case post -> postReportRepository.findByIdPostId(targetId).stream()
                    .map(r -> new TargetReportDto(r.getId().getReporterId(), reasonIdToText(r.getReasonId()),
                            statusName(r.getStatus()), r.getCreatedAt()))
                    .toList();
            case comment -> commentReportRepository.findByIdCommentId(targetId).stream()
                    .map(r -> new TargetReportDto(r.getId().getReporterId(), reasonIdToText(r.getReasonId()),
                            statusName(r.getStatus()), r.getCreatedAt()))
                    .toList();
            case profile -> profileReportRepository.findByIdProfileId(targetId).stream()
                    .map(r -> new TargetReportDto(r.getId().getReporterId(), reasonIdToText(r.getReasonId()),
                            statusName(r.getStatus()), r.getCreatedAt()))
                    .toList();
            case forum_thread -> forumThreadReportRepository.findByIdThreadId(targetId).stream()
                    .map(r -> new TargetReportDto(r.getId().getReporterId(), reasonIdToText(r.getReasonId()),
                            statusName(r.getStatus()), r.getCreatedAt()))
                    .toList();
            case forum_thread_reply -> forumThreadReplyReportRepository.findByIdReplyId(targetId).stream()
                    .map(r -> new TargetReportDto(r.getId().getReporterId(), reasonIdToText(r.getReasonId()),
                            statusName(r.getStatus()), r.getCreatedAt()))
                    .toList();
        };
        return reports.stream()
                .sorted(Comparator.comparing(TargetReportDto::createdAt,
                        Comparator.nullsLast(Comparator.<Instant>naturalOrder())).reversed())
                .toList();
    }

    @Override
    @Transactional
    public void updateReportsStatusForTarget(String targetType, UUID targetId, String status) {
        ReportStatus newStatus = ReportStatus.valueOf(status);
        if (newStatus != ReportStatus.resolved && newStatus != ReportStatus.dismissed) {
            throw new IllegalArgumentException("Reports can only be closed as resolved or dismissed, not " + status);
        }
        // A target has at most a handful of reports, so entity-level updates (dirty checking)
        // are fine and sidestep binding the native enum in a bulk JPQL update.
        switch (ReportTarget.valueOf(targetType)) {
            case post -> postReportRepository.findByIdPostId(targetId)
                    .forEach(r -> r.setStatus(newStatus));
            case comment -> commentReportRepository.findByIdCommentId(targetId)
                    .forEach(r -> r.setStatus(newStatus));
            case profile -> profileReportRepository.findByIdProfileId(targetId)
                    .forEach(r -> r.setStatus(newStatus));
            case forum_thread -> forumThreadReportRepository.findByIdThreadId(targetId)
                    .forEach(r -> r.setStatus(newStatus));
            case forum_thread_reply -> forumThreadReplyReportRepository.findByIdReplyId(targetId)
                    .forEach(r -> r.setStatus(newStatus));
        }
    }

    /** Single-row reason lookup — target report lists are small (one target's reports only). */
    private String reasonIdToText(UUID reasonId) {
        if (reasonId == null) {
            return null;
        }
        return reportReasonRepository.findById(reasonId)
                .map(ReportReasonEntity::getReason)
                .orElse(null);
    }

    private String statusName(ReportStatus status) {
        return status == null ? null : status.name();
    }

    private MyReportDto toDto(ReportTarget targetType, UUID targetId, UUID reasonId,
                             ReportStatus status, Instant createdAt, Map<UUID, String> reasonText) {
        return new MyReportDto(
                targetType.name(),
                targetId,
                reasonId == null ? null : reasonText.get(reasonId),
                status == null ? null : status.name(),
                createdAt);
    }

    private List<ReportReasonDto> listReasons(ReportTarget target) {
        return reportReasonRepository.findAllByTarget(target).stream()
                .map(reason -> new ReportReasonDto(reason.getId(), reason.getReason()))
                .toList();
    }

    /**
     * A reason is optional: {@code null} skips validation. When present it must exist and be scoped
     * to the target being reported — otherwise the client picked a reason from the wrong list.
     */
    private void validateReason(UUID reasonId, ReportTarget target) {
        if (reasonId == null) {
            return;
        }
        ReportReasonEntity reason = reportReasonRepository.findById(reasonId)
                .orElseThrow(() -> new InvalidReportReasonException(reasonId));
        if (reason.getTarget() != target) {
            throw new InvalidReportReasonException(reasonId);
        }
    }
}
