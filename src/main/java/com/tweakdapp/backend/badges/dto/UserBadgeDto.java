package com.tweakdapp.backend.badges.dto;

import java.time.Instant;

/**
 * A badge a user has unlocked, and when.
 *
 * <p>The badge itself is nested rather than flattened so the client renders a profile badge and a
 * catalogue badge with the same widget — the only thing a held badge adds is the date.
 *
 * @param badge     the badge, with its artwork URLs resolved
 * @param earnedAt  when it was unlocked
 */
public record UserBadgeDto(
        BadgeDto badge,
        Instant earnedAt
) {}
