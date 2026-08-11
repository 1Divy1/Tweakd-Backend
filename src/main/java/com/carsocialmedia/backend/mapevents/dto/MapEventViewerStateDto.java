package com.carsocialmedia.backend.mapevents.dto;

import java.util.List;
import java.util.UUID;

/**
 * Where the calling user stands on this event — everything the client needs to decide which buttons
 * to render, resolved server-side so the app never has to infer permissions from other fields.
 *
 * @param isCreator          whether the caller created the event (may delete it, manage organizers)
 * @param isOrganizer        whether the caller is an organizer (creator included)
 * @param canEdit            whether edits are still allowed — organizers, and only before approval
 * @param attendanceStatus   the caller's RSVP: {@code attending}, {@code interested}, or {@code null}
 * @param canRsvp            whether RSVP is currently open (the event has not finished or been cancelled)
 * @param canRegisterCars    whether cars may still be entered (category deadline not yet passed)
 * @param myRegisteredCarIds the caller's own cars entered into this event, whatever their status
 */
public record MapEventViewerStateDto(
        boolean isCreator,
        boolean isOrganizer,
        boolean canEdit,
        String attendanceStatus,
        boolean canRsvp,
        boolean canRegisterCars,
        List<UUID> myRegisteredCarIds
) {}
