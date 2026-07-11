package com.carsocialmedia.backend.report;

import com.carsocialmedia.backend.report.dto.MyReportDto;
import com.carsocialmedia.backend.report.dto.ReportReasonDto;

import java.util.List;
import java.util.UUID;

/**
 * Persistence helper for user-filed reports against posts, comments, and profiles.
 *
 * <p>This module owns the three report tables ({@code post_reports}, {@code comment_reports},
 * {@code profile_reports}) and the shared {@code report_reasons} reference data, but it deliberately
 * exposes <em>no</em> REST endpoints of its own. Reporting is a resource-oriented action, so the
 * endpoints live next to the thing being reported — in the {@code posts} and {@code profile} modules
 * — and delegate the actual insert here.
 *
 * <p>To keep this module a dependency-free leaf (and avoid a Modulith cycle), it does <b>not</b>
 * validate that the reported target exists or that the reporter is not the target's owner: the
 * calling module already knows its own resources and performs those checks before delegating. What
 * this service does own is the concerns that belong to the report tables themselves: rejecting a
 * {@code reasonId} that doesn't exist or doesn't match the target ({@code InvalidReportReasonException}),
 * and rejecting a duplicate report by the same reporter ({@code DuplicateReportException}).
 */
public interface ReportService {

    /**
     * Files a report against a post.
     *
     * @param reporterId the reporting user's profile UUID (assumed to exist — it is the JWT subject)
     * @param postId the reported post (its existence is validated by the caller / the DB FK)
     * @param reasonId a preset {@code report_reasons} row scoped to {@code post}, or {@code null}
     * @throws com.carsocialmedia.backend.report.exception.InvalidReportReasonException if {@code reasonId}
     *         is given but does not exist or is not a {@code post} reason
     * @throws com.carsocialmedia.backend.report.exception.DuplicateReportException if this reporter has
     *         already reported this post
     */
    void reportPost(UUID reporterId, UUID postId, UUID reasonId);

    /**
     * Files a report against a comment. See {@link #reportPost} for the parameter and exception
     * semantics ({@code reasonId} must be a {@code comment} reason).
     */
    void reportComment(UUID reporterId, UUID commentId, UUID reasonId);

    /**
     * Files a report against a profile. See {@link #reportPost} for the parameter and exception
     * semantics ({@code reasonId} must be a {@code profile} reason).
     */
    void reportProfile(UUID reporterId, UUID profileId, UUID reasonId);

    /**
     * Files a report against a forum thread. See {@link #reportPost} for the parameter and exception
     * semantics ({@code reasonId} must be a {@code forum_thread} reason).
     */
    void reportForumThread(UUID reporterId, UUID threadId, UUID reasonId);

    /**
     * Files a report against a forum thread reply. See {@link #reportPost} for the parameter and
     * exception semantics ({@code reasonId} must be a {@code forum_thread_reply} reason).
     */
    void reportForumReply(UUID reporterId, UUID replyId, UUID reasonId);

    /** The preset reasons a user may pick from when reporting a post. */
    List<ReportReasonDto> listPostReportReasons();

    /** The preset reasons a user may pick from when reporting a comment. */
    List<ReportReasonDto> listCommentReportReasons();

    /** The preset reasons a user may pick from when reporting a profile. */
    List<ReportReasonDto> listProfileReportReasons();

    /** The preset reasons a user may pick from when reporting a forum thread. */
    List<ReportReasonDto> listForumThreadReportReasons();

    /** The preset reasons a user may pick from when reporting a forum thread reply. */
    List<ReportReasonDto> listForumReplyReportReasons();

    /**
     * Lists every report filed by the given reporter — post, comment, and profile reports merged
     * into a single feed, newest first — for the reporter's own "my submitted reports" view.
     *
     * <p>Scoped strictly to {@code reporterId}, so a caller only ever sees their own reports. The
     * result is self-contained (target id, reason text, status, timestamp) and needs no lookup into
     * the {@code posts} / {@code profile} modules.
     *
     * @param reporterId the reporting user's profile UUID (the JWT subject)
     */
    List<MyReportDto> listMyReports(UUID reporterId);

    // ---- moderation (called by the admin module; no auth logic here) --------

    /**
     * Every report filed against one target, newest first — the "who reported this and why" panel
     * of a moderation case.
     *
     * @param targetType one of {@code post}, {@code comment}, {@code profile}, {@code forum_thread},
     *        {@code forum_thread_reply} (the {@code moderation_cases.target_type} values)
     * @param targetId the reported content / profile UUID
     * @throws IllegalArgumentException if {@code targetType} is not one of the five types
     */
    List<com.carsocialmedia.backend.report.dto.TargetReportDto> listReportsForTarget(String targetType, UUID targetId);

    /**
     * Closes out every report filed against one target — called when a moderator decides a case, so
     * the reporters' "my reports" feeds reflect the outcome. Must run <em>before</em> a content
     * hard-delete only if the caller still needs the rows for anything else; the report rows
     * themselves CASCADE away with the content, which is fine once the audit snapshot is written.
     *
     * @param targetType one of the five target types (see {@link #listReportsForTarget})
     * @param targetId the reported content / profile UUID
     * @param status the outcome: {@code "resolved"} or {@code "dismissed"}
     * @throws IllegalArgumentException if {@code targetType} or {@code status} is invalid
     */
    void updateReportsStatusForTarget(String targetType, UUID targetId, String status);
}
