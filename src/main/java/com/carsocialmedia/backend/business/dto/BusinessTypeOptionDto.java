package com.carsocialmedia.backend.business.dto;

/**
 * A selectable business category — reference data seeded in Supabase. The app uses these both to
 * render the map's type filter and to pick a marker icon per {@code id}.
 *
 * @param id    stable identifier (e.g. {@code tuning_shop}); safe to switch on client-side
 * @param label human-readable label (e.g. {@code "Tuning shop"})
 */
public record BusinessTypeOptionDto(
        String id,
        String label
) {}
