package com.tweakdapp.backend.badges.internal;

import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.dto.BadgeDto;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The badge endpoints: what the caller has unlocked, what they have left to unlock, and the full
 * catalogue.
 *
 * <p><strong>Another user's badges are not here.</strong> They ride in that user's profile
 * response, so the profile screen renders the badge row in the same round trip as the header — see
 * {@code ProfileService}. This module holds no username-keyed read at all, which is what keeps it
 * free of a dependency on {@code profile} and therefore available to it.
 *
 * <p>Reads, plus one write that cannot award anything. A badge is never <em>granted</em> over HTTP
 * by the person receiving it — it is awarded in-process by the module that witnessed the
 * achievement, or by staff through {@code /api/v1/admin/badges}. The single {@code POST} here only
 * lets the caller acknowledge that their app has played the unlock animation for a badge they
 * already hold, so it does not play again; it can neither create a badge nor change one.
 *
 * <p>Authentication is required, as it is everywhere outside {@code /public/**}.
 *
 * <p>Nothing here is paginated. A user holds a handful of badges and the catalogue is a curated
 * list, so both fit in one response; a cursor would be machinery around a single screen.
 */
@RestController
@RequestMapping("/api/v1/badges")
class BadgeController {

    private final BadgeService badgeService;

    BadgeController(BadgeService badgeService) {
        this.badgeService = badgeService;
    }

    /** The caller's own badges, newest unlock first. */
    @GetMapping("/me")
    public List<UserBadgeDto> getMyBadges(@AuthenticationPrincipal Jwt jwt) {
        return badgeService.listUserBadges(UUID.fromString(jwt.getSubject()));
    }

    /**
     * The badges the caller has <em>not</em> unlocked yet — the locked section of their own
     * profile, with locked artwork and the description that says how to earn each one.
     *
     * <p>Only ever the caller's own. What someone has left to achieve is not a stranger's business,
     * and there is deliberately no username-keyed equivalent.
     */
    @GetMapping("/me/locked")
    public List<BadgeDto> getMyLockedBadges(@AuthenticationPrincipal Jwt jwt) {
        return badgeService.listLockedBadges(UUID.fromString(jwt.getSubject()));
    }

    /**
     * The caller's earned badges whose one-time unlock animation the app still owes them, oldest
     * unlock first. The app calls this on launch, plays the celebration for each, then acknowledges
     * each through {@link #acknowledgeCelebration}.
     *
     * <p>Same shape as {@code /me} — a {@link UserBadgeDto} per row — so the client reuses the badge
     * widget. Only ever the caller's own.
     */
    @GetMapping("/me/pending-celebration")
    public List<UserBadgeDto> getMyPendingCelebrations(@AuthenticationPrincipal Jwt jwt) {
        return badgeService.listPendingCelebrations(UUID.fromString(jwt.getSubject()));
    }

    /**
     * Acknowledges that the app has finished the unlock animation for one badge, taking it off
     * {@link #getMyPendingCelebrations}. Idempotent: a repeat call, or one for a badge the caller
     * does not hold, returns {@code {"celebrated": false}} and changes nothing.
     *
     * <p>This is the module's only write, and it cannot award or alter a badge — it only flips the
     * celebration latch on a badge the caller already holds.
     */
    @PostMapping("/me/pending-celebration/{badgeId}")
    public Map<String, Boolean> acknowledgeCelebration(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable String badgeId) {
        boolean flipped = badgeService.markCelebrated(UUID.fromString(jwt.getSubject()), badgeId);
        return Map.of("celebrated", flipped);
    }

    /**
     * Every badge that can currently be unlocked — the "badges you can earn" sheet. Retired badges
     * are absent; each entry carries both the locked and unlocked artwork URLs, so the client can
     * render a collection screen by checking which of these the user holds.
     */
    @GetMapping("/catalogue")
    public List<BadgeDto> getCatalogue() {
        return badgeService.listCatalogue();
    }
}
