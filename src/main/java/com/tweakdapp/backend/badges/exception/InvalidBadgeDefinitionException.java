package com.tweakdapp.backend.badges.exception;

import com.tweakdapp.backend.shared.exception.BadRequestException;

/**
 * Raised when the dashboard saves a badge definition that could never work.
 *
 * <p>Both cases it covers fail <em>silently</em> if allowed through, which is why they are refused
 * at the edge rather than left to be discovered:
 *
 * <ul>
 *   <li>an <strong>unrecognised trigger code</strong> — the badge would match no event and simply
 *       never unlock, with nothing in a log to say so;</li>
 *   <li>a <strong>window that ends before it starts</strong> — the badge would be permanently
 *       unearnable while looking, on the dashboard, exactly like one that is on offer.</li>
 * </ul>
 *
 * <p>The database refuses both too ({@code badges_award_trigger_known_ck},
 * {@code badges_earnable_window_ck}). This is the half that says which field is wrong and what the
 * accepted values are, instead of surfacing a constraint name as a 500.
 */
public class InvalidBadgeDefinitionException extends BadRequestException {

    private InvalidBadgeDefinitionException(String message) {
        super(message);
    }

    public static InvalidBadgeDefinitionException unknownTrigger(String submitted, String known) {
        return new InvalidBadgeDefinitionException(
                "Unknown award trigger '" + submitted + "'. Leave it empty for a badge granted by hand,"
                        + " or use one of: " + known);
    }

    public static InvalidBadgeDefinitionException invalidWindow() {
        return new InvalidBadgeDefinitionException(
                "earnableFrom must be before earnableUntil — a badge whose window ends before it opens"
                        + " can never be awarded.");
    }
}
