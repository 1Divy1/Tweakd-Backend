package com.tweakdapp.backend.mapevents.dto;

/**
 * A contest category — reference data behind the create screen's tile grid.
 *
 * @param id    stable code ({@code exhaust}, {@code wheels}, …, {@code custom})
 * @param label display label ("Best exhaust system")
 * @param icon  glyph key the app maps to a local icon; unknown keys fall back to a trophy
 */
public record ContestCategoryDto(String id, String label, String icon) {}
