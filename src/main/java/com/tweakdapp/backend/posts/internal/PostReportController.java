package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.posts.PostsService;
import com.tweakdapp.backend.report.ReportService;
import com.tweakdapp.backend.report.dto.ReportReasonDto;
import com.tweakdapp.backend.report.dto.ReportRequest;
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
 * Reporting endpoints for posts and comments. They live in the {@code posts} module — next to the
 * resource being reported — and delegate the actual persistence to the {@code report} module:
 * post/comment existence and self-report checks run in {@link PostsService}, while the preset reason
 * lists are a straight passthrough to {@link ReportService}.
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostReportController {

    private final PostsService postsService;
    private final ReportService reportService;

    public PostReportController(PostsService postsService, ReportService reportService) {
        this.postsService = postsService;
        this.reportService = reportService;
    }

    /** The preset reasons a user may pick from when reporting a post. */
    @GetMapping("/report-reasons")
    public List<ReportReasonDto> getPostReportReasons() {
        return reportService.listPostReportReasons();
    }

    /** The preset reasons a user may pick from when reporting a comment. */
    @GetMapping("/comments/report-reasons")
    public List<ReportReasonDto> getCommentReportReasons() {
        return reportService.listCommentReportReasons();
    }

    /** Files a report against a post. The body (and its {@code reasonId}) is optional. */
    @PostMapping("/{postId}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportPost(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable UUID postId,
                           @RequestBody(required = false) ReportRequest request) {
        postsService.reportPost(jwt.getSubject(), postId, request == null ? null : request.reasonId());
    }

    /** Files a report against a comment. The body (and its {@code reasonId}) is optional. */
    @PostMapping("/{postId}/comments/{commentId}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportComment(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID postId,
                              @PathVariable UUID commentId,
                              @RequestBody(required = false) ReportRequest request) {
        postsService.reportComment(jwt.getSubject(), postId, commentId, request == null ? null : request.reasonId());
    }
}
