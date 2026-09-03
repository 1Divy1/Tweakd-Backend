package com.tweakdapp.backend.admin.internal.dto;

import com.tweakdapp.backend.badges.dto.BadgeDto;

/**
 * A badge as the dashboard's catalogue page lists it: the definition, plus how many users hold it.
 *
 * <p>The count lives here rather than on {@link BadgeDto} because it is an administrative fact —
 * the app has no use for it, and putting it on the shared DTO would mean counting holders on every
 * profile read.
 *
 * @param badge   the definition, retired ones included
 * @param holders how many users have unlocked it. Non-zero is what makes the badge undeletable
 */
public record AdminBadgeDto(
        BadgeDto badge,
        long holders
) {}
