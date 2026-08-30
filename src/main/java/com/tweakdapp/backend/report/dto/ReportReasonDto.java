package com.tweakdapp.backend.report.dto;

import java.util.UUID;

/**
 * One preset report reason, for populating the reason picker in the report UI. The target is implied
 * by the endpoint that returns it, so it is not repeated here.
 *
 * @param id     the {@code report_reasons} row id — sent back as {@code reasonId} when filing a report
 * @param reason the human-readable reason text
 */
public record ReportReasonDto(UUID id, String reason) {
}
