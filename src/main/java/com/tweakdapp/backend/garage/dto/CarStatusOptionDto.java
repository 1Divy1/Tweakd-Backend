package com.tweakdapp.backend.garage.dto;

/**
 * A status or role for a car (e.g., daily driver, project car, collector).
 *
 * @param id the status option ID (typically a string identifier)
 * @param type the status type name (e.g., "Daily Driver", "Project Car")
 */
public record CarStatusOptionDto(String id, String type) {}