package com.tweakdapp.backend.mapevents.dto;

import java.util.List;

/**
 * One event a car attended, with whatever it won there.
 *
 * @param event       the event
 * @param entryStatus the car's participation status — always {@code accepted} today, carried so
 *                    the shape can grow
 * @param placements  podium places in that event's contests, rank ascending; usually empty
 */
public record CarEventHistoryItemDto(
        CarEventHistoryEventDto event,
        String entryStatus,
        List<CarEventPlacementDto> placements
) {}
