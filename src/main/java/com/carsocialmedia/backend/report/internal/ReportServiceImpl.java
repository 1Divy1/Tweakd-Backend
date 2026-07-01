package com.carsocialmedia.backend.report.internal;

import com.carsocialmedia.backend.report.ReportService;
import com.carsocialmedia.backend.report.dto.MyReportDto;
import com.carsocialmedia.backend.report.dto.ReportReasonDto;
import com.carsocialmedia.backend.report.exception.DuplicateReportException;
import com.carsocialmedia.backend.report.exception.InvalidReportReasonException;
import com.carsocialmedia.backend.report.internal.entities.CommentReportEntity;
import com.carsocialmedia.backend.report.internal.entities.CommentReportId;
import com.carsocialmedia.backend.report.internal.entities.PostReportEntity;
import com.carsocialmedia.backend.report.internal.entities.PostReportId;
import com.carsocialmedia.backend.report.internal.entities.ProfileReportEntity;
import com.carsocialmedia.backend.report.internal.entities.ProfileReportId;
import com.carsocialmedia.backend.report.internal.entities.ReportReasonEntity;
import com.carsocialmedia.backend.report.internal.enums.ReportStatus;
import com.carsocialmedia.backend.report.internal.enums.ReportTarget;
import com.carsocialmedia.backend.report.internal.repositories.CommentReportRepository;
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
    private final ReportReasonRepository reportReasonRepository;

    public ReportServiceImpl(PostReportRepository postReportRepository,
                             CommentReportRepository commentReportRepository,
                             ProfileReportRepository profileReportRepository,
                             ReportReasonRepository reportReasonRepository) {
        this.postReportRepository = postReportRepository;
        this.commentReportRepository = commentReportRepository;
        this.profileReportRepository = profileReportRepository;
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
    public List<MyReportDto> listMyReports(UUID reporterId) {
        List<PostReportEntity> postReports = postReportRepository.findByIdReporterId(reporterId);
        List<CommentReportEntity> commentReports = commentReportRepository.findByIdReporterId(reporterId);
        List<ProfileReportEntity> profileReports = profileReportRepository.findByIdReporterId(reporterId);

        // Resolve all referenced reason texts in one batch to avoid an N+1 across the three lists.
        List<UUID> reasonIds = Stream.of(
                        postReports.stream().map(PostReportEntity::getReasonId),
                        commentReports.stream().map(CommentReportEntity::getReasonId),
                        profileReports.stream().map(ProfileReportEntity::getReasonId))
                .flatMap(Function.identity())
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<UUID, String> reasonText = reportReasonRepository.findAllById(reasonIds).stream()
                .collect(Collectors.toMap(ReportReasonEntity::getId, ReportReasonEntity::getReason));

        List<MyReportDto> reports = new ArrayList<>(postReports.size() + commentReports.size() + profileReports.size());
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

        // Newest first across all three kinds.
        reports.sort(Comparator.comparing(MyReportDto::createdAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return reports;
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
