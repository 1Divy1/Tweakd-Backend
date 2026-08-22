/**
 * Data Transfer Objects for the Map Events module.
 *
 * <ul>
 *   <li>{@link com.tweakdapp.backend.mapevents.dto.MapEventPinDto} — map marker <em>and</em>
 *       the floating widget shown when a pin is tapped</li>
 *   <li>{@link com.tweakdapp.backend.mapevents.dto.MapEventDto} — full event page</li>
 *   <li>{@link com.tweakdapp.backend.mapevents.dto.MapEventViewerStateDto} — what the calling
 *       user may do on that page</li>
 *   <li>{@link com.tweakdapp.backend.mapevents.dto.MapEventOrganizerDto} — one organizer
 *       credit (individual or business)</li>
 *   <li>{@link com.tweakdapp.backend.mapevents.dto.MapEventAttendeeDto} /
 *       {@link com.tweakdapp.backend.mapevents.dto.MapEventParticipantDto} — the spectator
 *       list and the car line-up</li>
 *   <li>{@link com.tweakdapp.backend.mapevents.dto.CarMeetDetailsDto} — car-meet specific
 *       detail; one of these per subcategory as more are added</li>
 *   <li>{@link com.tweakdapp.backend.mapevents.dto.MapEventCategoryDto} — subcategory
 *       reference data</li>
 *   <li>{@link com.tweakdapp.backend.mapevents.dto.MapEventPageDto} — one keyset page</li>
 * </ul>
 *
 * <p>Exposed as a named interface so other modules — the {@code admin} dashboard in particular —
 * can consume these DTOs without crossing into {@code mapevents.internal}.
 */
@NamedInterface("dto")
package com.tweakdapp.backend.mapevents.dto;

import org.springframework.modulith.NamedInterface;
