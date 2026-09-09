package com.tweakdapp.backend.mapevents.dto;

import java.util.List;
import java.util.UUID;

/**
 * Where the calling user stands on this contest — resolved server-side so the app never has to
 * infer permissions from other fields.
 *
 * @param isOrganizer whether the caller organizes the event (may edit, finish, decide entries)
 * @param canVote     whether a vote from the caller would be accepted right now: they RSVP'd
 *                    {@code attending}, voting is open, and the event is not over
 * @param voteCarId   the car the caller currently votes for, or {@code null}
 * @param canEnter    whether the caller has an accepted event car that could still be entered
 * @param myEntries   the caller's own cars in this contest, whatever their status
 */
public record ContestViewerStateDto(
        boolean isOrganizer,
        boolean canVote,
        UUID voteCarId,
        boolean canEnter,
        List<ContestMyEntryDto> myEntries
) {}
