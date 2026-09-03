package com.tweakdapp.backend.reputation.dto;

import java.util.UUID;

/**
 * The thing that earned a reputation entry — "attended <em>this</em> event", not merely "attended
 * an event".
 *
 * <p>Passing one to {@code ReputationService.award} does two things. It makes the entry
 * deep-linkable, so a profile timeline can send the reader to the event, thread or car behind the
 * points. And it makes the award <strong>idempotent</strong>: a second award of the same reason for
 * the same source returns the entry already on file instead of paying twice, enforced by a unique
 * index rather than by every caller remembering to check.
 *
 * <p>{@code label} is a <em>snapshot</em>, deliberately. It is stored on the history row rather
 * than joined at read time, so an entry stays readable after the source is renamed or deleted, and
 * so this module never has to read another module's tables to render a timeline. Pass what the user
 * would recognise — an event's title, a thread's subject, a car's name.
 *
 * @param type  one of the {@link com.tweakdapp.backend.reputation.ReputationSourceType} constants
 * @param id    the source row's id
 * @param label the source's display name right now; may be {@code null} if it genuinely has none,
 *              in which case clients fall back to the reason's own label
 */
public record ReputationSource(String type, UUID id, String label) {

    public ReputationSource {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Reputation source type is required");
        }
        if (id == null) {
            throw new IllegalArgumentException("Reputation source id is required");
        }
        // Normalise here so "" and "   " do not reach the column as content that renders as an
        // empty line in the timeline.
        label = (label == null || label.isBlank()) ? null : label.strip();
    }

    /** A source with no meaningful display name of its own. */
    public static ReputationSource of(String type, UUID id) {
        return new ReputationSource(type, id, null);
    }
}
