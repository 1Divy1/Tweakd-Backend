package com.tweakdapp.backend.mapevents.dto;

import java.util.UUID;

/**
 * One rule on the event page, in display order.
 *
 * @param id        the {@code car_event_organizer_rules} row id
 * @param rule      the rule text
 * @param sortOrder display position, 0-based
 */
public record MapEventRuleDto(
        UUID id,
        String rule,
        int sortOrder
) {}
