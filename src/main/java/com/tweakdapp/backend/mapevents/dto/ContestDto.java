package com.tweakdapp.backend.mapevents.dto;

import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A contest in full, with its ranked ballot embedded. Ballots are bounded (at most 40 accepted
 * entries), so there is nothing to paginate and one read paints the whole screen.
 *
 * @param id             contest id
 * @param eventId        the event it runs inside
 * @param category       its category
 * @param title          display title
 * @param criteria       how attendees should judge it, or {@code null}
 * @param status         {@code scheduled}, {@code open}, {@code finished} or {@code canceled}
 * @param opensAt        when voting is planned to open (a label; an organizer opens it)
 * @param closesAt       when voting is planned to close (a label; an organizer finishes it)
 * @param finishedAt     when it was finalised, or {@code null}
 * @param finishedEarly  whether it was finished before its planned {@code closesAt}
 * @param votesCount     total votes cast
 * @param entriesCount   accepted entries
 * @param entries        the accepted entries, ranked; each car appears once
 * @param viewer         the caller's standing
 * @param pendingEntries entry requests awaiting a decision — organizers only, else empty
 * @param createdBy      the organizer who set it up ("Set by @handle"); {@code null} if their
 *                       profile no longer resolves
 * @param createdAt      when it was created
 * @param event          the event it runs inside — title, cover, place, head-counts — so a contest
 *                       read carries its own context (see {@link ContestEventSummaryDto})
 */
public record ContestDto(
        UUID id,
        UUID eventId,
        ContestCategoryDto category,
        String title,
        String criteria,
        String status,
        Instant opensAt,
        Instant closesAt,
        Instant finishedAt,
        boolean finishedEarly,
        int votesCount,
        int entriesCount,
        List<ContestEntryDto> entries,
        ContestViewerStateDto viewer,
        List<ContestPendingEntryDto> pendingEntries,
        ProfileSearchResultDto createdBy,
        Instant createdAt,
        ContestEventSummaryDto event
) {}
