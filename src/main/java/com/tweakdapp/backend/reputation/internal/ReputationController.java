package com.tweakdapp.backend.reputation.internal;

import com.tweakdapp.backend.reputation.ReputationService;
import com.tweakdapp.backend.reputation.dto.ReputationHistoryPageDto;
import com.tweakdapp.backend.reputation.dto.ReputationReasonDto;
import com.tweakdapp.backend.reputation.dto.ReputationSummaryDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The reputation endpoints: the score block and the history timeline shown on a profile, plus the
 * catalogue behind the "how reputation works" sheet.
 *
 * <p>All reads. Reputation is never granted over HTTP — it is awarded by the module that witnessed
 * the achievement, through {@link ReputationService}, so there is no endpoint a client could call
 * to inflate its own score.
 *
 * <p>Another user's reputation is public by design: the whole point is that a stranger can inspect
 * it before trusting them. Authentication is still required, as it is everywhere outside
 * {@code /public/**}.
 *
 * <p>The two history endpoints are deliberately not the same read. {@code /me/history} includes
 * revoked entries, so a user whose score dropped can see why; {@code /users/{username}/history}
 * omits them entirely. Revocation is between the user and the platform — publishing it would make
 * the timeline a shaming mechanic. That split is enforced in the service by which method the
 * controller calls, and the username-keyed one has no way to ask for the revoked view.
 */
@RestController
@RequestMapping("/api/v1/reputation")
class ReputationController {

    private final ReputationService reputationService;

    ReputationController(ReputationService reputationService) {
        this.reputationService = reputationService;
    }

    /** The caller's own reputation block. */
    @GetMapping("/me")
    public ReputationSummaryDto getMySummary(@AuthenticationPrincipal Jwt jwt) {
        return reputationService.getSummary(UUID.fromString(jwt.getSubject()));
    }

    /**
     * One keyset page of the caller's own history, newest first — <em>including</em> revoked
     * entries, each carrying {@code revoked_at} and {@code revoked_reason}.
     */
    @GetMapping("/me/history")
    public ReputationHistoryPageDto getMyHistory(@AuthenticationPrincipal Jwt jwt,
                                                 @RequestParam(required = false) String cursor,
                                                 @RequestParam(defaultValue = "20") int size) {
        return reputationService.getHistory(UUID.fromString(jwt.getSubject()), cursor, size);
    }

    /** Any user's reputation block, for their public profile screen. */
    @GetMapping("/users/{username}")
    public ReputationSummaryDto getSummary(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) {
        return reputationService.getSummaryByUsername(UUID.fromString(jwt.getSubject()), username);
    }

    /**
     * One keyset page of any user's history — the receipts behind their score. Revoked entries are
     * absent, not flagged: what strangers see is what still stands.
     */
    @GetMapping("/users/{username}/history")
    public ReputationHistoryPageDto getHistory(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable String username,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(defaultValue = "20") int size) {
        return reputationService.getHistoryByUsername(UUID.fromString(jwt.getSubject()), username, cursor, size);
    }

    /** The active catalogue: every way to earn (or lose) reputation, grouped by category. */
    @GetMapping("/reasons")
    public List<ReputationReasonDto> listReasons() {
        return reputationService.listReasons();
    }
}
