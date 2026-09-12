package com.tweakdapp.backend.admin.internal.controllers;

import com.tweakdapp.backend.admin.internal.AdminAccessService;
import com.tweakdapp.backend.admin.internal.Capability;
import com.tweakdapp.backend.admin.internal.dto.AdminBadgeDto;
import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.dto.BadgeDto;
import com.tweakdapp.backend.badges.dto.BadgeGrantDto;
import com.tweakdapp.backend.badges.dto.BadgeUpsertRequest;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Badges page of the dashboard: curating the catalogue, and handing a badge to a specific user
 * (or taking it back).
 *
 * <p>Everything here is gated on {@link Capability#MANAGE_BADGES} — owner and senior admin only.
 * The badges granted from this page are precisely the ones no rule can decide: "was here early",
 * "helped at the meet". That is a recognition call about a named person, which is why it does not
 * ride along with content moderation.
 *
 * <p>The badge code sits in the path on create as well as update. It is not editable afterwards —
 * every unlock references it — so it is chosen once, and putting it where the resource is addressed
 * makes that visible rather than burying it in a body field that later requests must omit.
 */
@RestController
@RequestMapping("/api/v1/admin/badges")
class AdminBadgesController {

    private final AdminAccessService access;
    private final BadgeService badgeService;
    private final StorageService storageService;

    AdminBadgesController(AdminAccessService access, BadgeService badgeService, StorageService storageService) {
        this.access = access;
        this.badgeService = badgeService;
        this.storageService = storageService;
    }

    /**
     * The public URL prefix badge artwork is served from, with no trailing slash.
     *
     * <p>Badge SVGs are uploaded to R2 out of band — nothing presigns into the shared assets
     * bucket — so the create/edit form takes an object <em>key</em>, and this is what lets it show
     * the image the key resolves to before the badge is saved. One call per page load; the
     * dashboard concatenates {@code baseUrl + "/" + key} itself.
     */
    @GetMapping("/artwork-base-url")
    public Map<String, String> artworkBaseUrl(@AuthenticationPrincipal Jwt jwt) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_BADGES);
        return Map.of("baseUrl", storageService.publicBaseUrl(StorageBucket.ASSETS));
    }

    // ---- the catalogue ------------------------------------------------------

    /**
     * The whole catalogue with holder counts, retired badges included — the dashboard has to show
     * what was retired in order to bring it back.
     */
    @GetMapping
    public List<AdminBadgeDto> list(@AuthenticationPrincipal Jwt jwt) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_BADGES);

        Map<String, Long> holders = badgeService.holderCounts();
        return badgeService.listAllForAdmin().stream()
                .map(badge -> new AdminBadgeDto(badge, holders.getOrDefault(badge.id(), 0L)))
                .toList();
    }

    /**
     * Adds a badge to the catalogue. 409 if the code is taken — a badge is never silently
     * redefined, because its code is what every existing unlock points at.
     *
     * <p>The artwork itself is uploaded to the {@code app-assets} R2 bucket out of band; the body
     * carries the object <em>keys</em>, which is why they must not be full URLs.
     */
    @PostMapping("/{badgeId}")
    @ResponseStatus(HttpStatus.CREATED)
    public BadgeDto create(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable String badgeId,
                           @Valid @RequestBody BadgeUpsertRequest request) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_BADGES);
        return badgeService.create(badgeId, request);
    }

    /**
     * Edits a badge definition. Setting {@code available: false} retires it: it stops being
     * awardable and leaves the catalogue, while every profile already holding it is untouched.
     */
    @PutMapping("/{badgeId}")
    public BadgeDto update(@AuthenticationPrincipal Jwt jwt,
                           @PathVariable String badgeId,
                           @Valid @RequestBody BadgeUpsertRequest request) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_BADGES);
        return badgeService.update(badgeId, request);
    }

    /**
     * Deletes a badge outright. Only possible while nobody holds it; once awarded, retiring is the
     * only way to withdraw one (409 otherwise, saying how many users hold it).
     */
    @DeleteMapping("/{badgeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String badgeId) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_BADGES);
        badgeService.delete(badgeId);
    }

    // ---- granting to a user -------------------------------------------------

    /**
     * Grants a badge to one user. Idempotent — granting one they already hold returns what they
     * hold, with the original unlock date intact.
     *
     * <p>The granting staff member is recorded on the row, so a badge that appears on someone's
     * profile can always be traced back to whoever put it there.
     */
    @PostMapping("/{badgeId}/holders/{userId}")
    public UserBadgeDto grant(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable String badgeId,
                              @PathVariable UUID userId) {
        UUID staffId = UUID.fromString(jwt.getSubject());
        access.require(staffId, Capability.MANAGE_BADGES);
        return badgeService.grant(userId, badgeId, staffId);
    }

    /**
     * Takes a badge back from one user — for a grant that went to the wrong account, or was made in
     * error. The row is deleted, so the badge becomes earnable again. Idempotent.
     */
    @DeleteMapping("/{badgeId}/holders/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal Jwt jwt,
                       @PathVariable String badgeId,
                       @PathVariable UUID userId) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_BADGES);
        badgeService.revoke(userId, badgeId);
    }

    /**
     * What one user currently holds — the badge panel on a user's dashboard detail page.
     *
     * <p>This is the <strong>only</strong> place {@code granted_by} is ever returned. A badge on a
     * profile is the user's achievement; "granted by X" next to it in the app would read as a
     * favour rather than something earned, so nothing the app returns carries it. It is recorded so
     * a hand-grant can be traced afterwards, and read here when someone asks who did what.
     * {@code null} means the backend awarded it automatically.
     */
    @GetMapping("/holders/{userId}")
    public List<BadgeGrantDto> listUserBadges(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
        access.require(UUID.fromString(jwt.getSubject()), Capability.MANAGE_BADGES);
        return badgeService.listGrantsForAdmin(userId);
    }
}
