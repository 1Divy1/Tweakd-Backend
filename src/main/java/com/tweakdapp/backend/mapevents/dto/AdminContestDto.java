package com.tweakdapp.backend.mapevents.dto;

import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the dashboard's contest-oversight list.
 *
 * <p>It exists because nothing closes a contest on a timer any more (see
 * {@code CONTESTS_MANUAL_LIFECYCLE.md}): a contest opens and finishes when an organizer taps, or
 * when the event itself finishes. An organizer who walks away leaves a contest taking votes
 * indefinitely, and this list — plus the force-finish behind it — is the only way to see and end
 * that from outside the app.
 *
 * <p>Deliberately flat and cheap: no entries, no viewer state, no vote breakdown. A reviewer needs
 * to spot the abandoned ones, not to judge the cars.
 *
 * @param plannedEndPassed whether {@code closesAt} is already in the past — the "this one is
 *                         overdue" signal the list sorts and filters on. It is not a rule: an open
 *                         contest legitimately keeps taking votes past its planned end
 * @param eventEndsAt      when the event itself ends; a contest still open long after this is the
 *                         strongest sign the organizer is gone
 * @param createdBy        the organizer who created it, for the reviewer to contact first
 */
public record AdminContestDto(
        UUID id,
        UUID eventId,
        String eventTitle,
        String eventStatus,
        Instant eventEndsAt,
        String title,
        String categoryId,
        String categoryLabel,
        String status,
        Instant opensAt,
        Instant closesAt,
        boolean plannedEndPassed,
        int entriesCount,
        int votesCount,
        ProfileSearchResultDto createdBy,
        Instant createdAt
) {}
