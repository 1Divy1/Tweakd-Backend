/**
 * Data Transfer Objects for the Badges module.
 *
 * <ul>
 *   <li>{@link com.tweakdapp.backend.badges.dto.BadgeDto} — one catalogue entry, with both artwork
 *       URLs already resolved to full public R2 URLs</li>
 *   <li>{@link com.tweakdapp.backend.badges.dto.UserBadgeDto} — a badge a specific user holds,
 *       plus when they unlocked it</li>
 *   <li>{@link com.tweakdapp.backend.badges.dto.BadgeUpsertRequest} — the admin write shape for
 *       creating or editing a badge definition</li>
 * </ul>
 *
 * <p>Exposed as a named interface so sibling modules (the {@code admin} dashboard today, whichever
 * module witnesses an achievement tomorrow) can consume these without crossing into
 * {@code badges.internal}.
 */
@NamedInterface("dto")
package com.tweakdapp.backend.badges.dto;

import org.springframework.modulith.NamedInterface;
