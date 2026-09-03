package com.tweakdapp.backend.badges.internal;

import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.dto.BadgeDto;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
 * <p>All reads. A badge is never granted over HTTP by the person receiving it — it is awarded
 * in-process by the module that witnessed the achievement, or by staff through
 * {@code /api/v1/admin/badges}. There is no endpoint a client could call to award itself one.
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
     * Every badge that can currently be unlocked — the "badges you can earn" sheet. Retired badges
     * are absent; each entry carries both the locked and unlocked artwork URLs, so the client can
     * render a collection screen by checking which of these the user holds.
     */
    @GetMapping("/catalogue")
    public List<BadgeDto> getCatalogue() {
        return badgeService.listCatalogue();
    }
}
