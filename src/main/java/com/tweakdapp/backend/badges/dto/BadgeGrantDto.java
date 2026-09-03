package com.tweakdapp.backend.badges.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A badge a user holds, as the <strong>dashboard</strong> sees it: the same thing
 * {@link UserBadgeDto} carries, plus who put it there.
 *
 * <p>A separate DTO precisely so the granter cannot leak into an app response. On a profile a badge
 * is the user's achievement; "granted by X" alongside it would read as a favour rather than
 * something earned. The column exists so a hand-grant can be traced afterwards, and this is the
 * only shape that exposes it.
 *
 * @param badge     the badge, with its artwork URLs resolved
 * @param earnedAt  when it was unlocked
 * @param grantedBy the staff member who granted it by hand, or {@code null} if the backend awarded
 *                  it automatically — which is how the two are told apart
 */
public record BadgeGrantDto(
        BadgeDto badge,
        Instant earnedAt,
        UUID grantedBy
) {}
