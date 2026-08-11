/**
 * Request payloads for the Map Events module.
 *
 * <ul>
 *   <li>{@link com.carsocialmedia.backend.mapevents.dto.request.CreateMapEventRequest} /
 *       {@link com.carsocialmedia.backend.mapevents.dto.request.UpdateMapEventRequest} — authoring
 *       an event (the cover image is attached separately, after upload)</li>
 *   <li>{@link com.carsocialmedia.backend.mapevents.dto.request.CoverImageKeyRequest} — attaching
 *       the uploaded cover's R2 key</li>
 *   <li>{@link com.carsocialmedia.backend.mapevents.dto.request.AddOrganizerRequest} — crediting a
 *       co-organizer (user or business)</li>
 *   <li>{@link com.carsocialmedia.backend.mapevents.dto.request.AttendanceRequest} — RSVP</li>
 *   <li>{@link com.carsocialmedia.backend.mapevents.dto.request.RegisterCarRequest} /
 *       {@link com.carsocialmedia.backend.mapevents.dto.request.ParticipantDecisionRequest} —
 *       entering a car, and the organizer's verdict on it</li>
 * </ul>
 */
@NamedInterface("dto-request")
package com.carsocialmedia.backend.mapevents.dto.request;

import org.springframework.modulith.NamedInterface;
