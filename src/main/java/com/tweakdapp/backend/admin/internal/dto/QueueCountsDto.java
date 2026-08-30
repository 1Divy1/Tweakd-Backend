package com.tweakdapp.backend.admin.internal.dto;

import java.util.Map;

/**
 * The moderation queue's badge numbers.
 *
 * @param pendingByType open + escalated cases per target type (the filter tabs)
 * @param byStatus      all cases per status (open / escalated / resolved)
 */
public record QueueCountsDto(
        Map<String, Long> pendingByType,
        Map<String, Long> byStatus) {
}
