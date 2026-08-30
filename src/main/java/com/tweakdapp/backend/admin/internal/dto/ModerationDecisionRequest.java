package com.tweakdapp.backend.admin.internal.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The moderator's decision payload, shared by all case actions.
 *
 * @param note optional context; for {@code warn} it is the message shown to the author
 * @param banDays only for {@code ban}: temp-ban length in days, {@code null} = permanent
 */
public record ModerationDecisionRequest(
        @Size(max = 1000) String note,
        @Positive Integer banDays) {
}
