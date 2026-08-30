package com.tweakdapp.backend.garage.dto;

/**
 * A drivetrain type (e.g., FWD, RWD, AWD).
 *
 * @param id the drivetrain ID (typically a string identifier)
 * @param name the drivetrain name (e.g., "Front-Wheel Drive", "Rear-Wheel Drive")
 */
public record CarDrivetrainDto(String id, String name) {}
