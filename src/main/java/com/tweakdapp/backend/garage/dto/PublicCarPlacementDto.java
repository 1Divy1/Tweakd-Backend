package com.tweakdapp.backend.garage.dto;

/**
 * A podium place in a finished contest: what the app and the public page draw as a contest badge.
 *
 * @param contestTitle the contest's title
 * @param categoryIcon glyph key ({@code exhaust}, {@code wheels}, …); unknown keys draw a trophy
 * @param rank         1, 2 or 3
 */
public record PublicCarPlacementDto(
        String contestTitle,
        String categoryIcon,
        int rank
) {}
