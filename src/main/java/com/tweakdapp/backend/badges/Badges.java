package com.tweakdapp.backend.badges;

/**
 * The badge codes the backend awards by name, so a typo is a compile error rather than a badge that
 * silently never unlocks.
 *
 * <p>Only badges the backend itself awards need a constant here. Purely hand-granted badges live in
 * the {@code badges} table and never appear in code — adding one is an admin action, not a deploy.
 *
 * <p>A code listed here must exist in the {@code badges} table; {@code award} refuses an unknown or
 * retired one.
 */
public final class Badges {

    /**
     * Early community member. Hand-granted from the dashboard — "was here early" is a judgement
     * call about which cohort counts, not a rule the backend can evaluate. Listed here so the
     * constant exists if that ever changes.
     */
    public static final String PIONEER = "pioneer";

    private Badges() {}
}
