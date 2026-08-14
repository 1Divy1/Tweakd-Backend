package com.carsocialmedia.backend.mapevents.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Replaces an event's rules list wholesale, in the given order — {@code sort_order} is assigned
 * server-side from list position. Only allowed while the event is pending or rejected; approval
 * locks the rules exactly as it locks the rest of the event.
 *
 * @param rules the full ordered list of rule texts; an empty list clears all rules
 */
public record ReplaceMapEventRulesRequest(
        @NotNull @Size(max = 50) List<@NotBlank @Size(max = 300) String> rules
) {}
