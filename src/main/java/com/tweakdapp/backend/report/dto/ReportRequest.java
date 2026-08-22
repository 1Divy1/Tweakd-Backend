package com.tweakdapp.backend.report.dto;

import java.util.UUID;

/**
 * Request body for filing a report. The reporter and the reported target come from the URL / JWT;
 * the only client-supplied field is an optional preset reason.
 *
 * @param reasonId a chosen {@code report_reasons} row scoped to the target being reported, or
 *                 {@code null} if the reporter did not pick a preset reason
 */
public record ReportRequest(UUID reasonId) {
}
