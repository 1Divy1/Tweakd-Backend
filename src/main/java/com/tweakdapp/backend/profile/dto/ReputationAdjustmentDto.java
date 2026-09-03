package com.tweakdapp.backend.profile.dto;

/**
 * The result of moving a profile's reputation score, returned by
 * {@link com.tweakdapp.backend.profile.ProfileService#applyReputationDelta}.
 *
 * <p>Both ends of the move are reported because the caller — the {@code reputation} module — has to
 * record them verbatim on the history row it writes in the same transaction. Deriving
 * {@code previousScore} as {@code newScore - delta} would be wrong whenever a penalty was clamped
 * at zero.
 *
 * @param previousScore the score before the change
 * @param newScore      the score after the change, never negative
 */
public record ReputationAdjustmentDto(int previousScore, int newScore) {

    /** The delta actually applied — differs from the requested one when a penalty hit the floor. */
    public int appliedDelta() {
        return newScore - previousScore;
    }
}
