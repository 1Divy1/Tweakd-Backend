/**
 * Data Transfer Objects for the Reputation module.
 *
 * <ul>
 *   <li>{@link com.tweakdapp.backend.reputation.dto.ReputationSummaryDto} — the profile header:
 *       the total, how many achievements built it, and the per-category split</li>
 *   <li>{@link com.tweakdapp.backend.reputation.dto.ReputationEntryDto} — one dated entry in the
 *       history timeline, denormalised with its reason's label so the client needs no second lookup</li>
 *   <li>{@link com.tweakdapp.backend.reputation.dto.ReputationHistoryPageDto} — one keyset page of
 *       that timeline</li>
 *   <li>{@link com.tweakdapp.backend.reputation.dto.ReputationReasonDto} — the catalogue entry,
 *       backing the "how reputation works" sheet</li>
 * </ul>
 *
 * <p>Exposed as a named interface so sibling modules (and, later, the marketplace) can consume
 * these DTOs without crossing into {@code reputation.internal}.
 */
@NamedInterface("dto")
package com.tweakdapp.backend.reputation.dto;

import org.springframework.modulith.NamedInterface;
