/**
 * Exceptions the Badges module raises, all extending the shared hierarchy in
 * {@code shared/exception} so the global handler maps them to the standard {@code ErrorResponse}.
 *
 * <ul>
 *   <li>{@link com.tweakdapp.backend.badges.exception.BadgeNotFoundException} — 404: unknown badge
 *       code, or one retired on a write path</li>
 *   <li>{@link com.tweakdapp.backend.badges.exception.BadgeAlreadyExistsException} — 409: creating
 *       a badge whose code is taken</li>
 *   <li>{@link com.tweakdapp.backend.badges.exception.BadgeInUseException} — 409: deleting a badge
 *       users still hold</li>
 * </ul>
 */
@NamedInterface("exception")
package com.tweakdapp.backend.badges.exception;

import org.springframework.modulith.NamedInterface;
