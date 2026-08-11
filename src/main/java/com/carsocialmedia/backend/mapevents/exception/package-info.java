/**
 * Custom exceptions for the Map Events module.
 *
 * <ul>
 *   <li><strong>NotFoundException (404)</strong> —
 *       {@link com.carsocialmedia.backend.mapevents.exception.MapEventNotFoundException}: unknown
 *       event, or an unapproved one the caller may not see</li>
 *   <li><strong>ForbiddenException (403)</strong> —
 *       {@link com.carsocialmedia.backend.mapevents.exception.NotEventOrganizerException}: an
 *       organizer- or creator-only action attempted by someone else;
 *       {@link com.carsocialmedia.backend.mapevents.exception.CarNotOwnedException}: entering
 *       somebody else's car</li>
 *   <li><strong>ConflictException (409)</strong> —
 *       {@link com.carsocialmedia.backend.mapevents.exception.EventNotEditableException}: editing an
 *       approved, cancelled or finished event;
 *       {@link com.carsocialmedia.backend.mapevents.exception.EventClosedException}: RSVP or car
 *       registration after the relevant deadline</li>
 *   <li><strong>BadRequestException (400)</strong> —
 *       {@link com.carsocialmedia.backend.mapevents.exception.InvalidMapEventException}: an
 *       inconsistent payload or unknown reference;
 *       {@link com.carsocialmedia.backend.mapevents.exception.InvalidSearchAreaException}: map
 *       search bounds;
 *       {@link com.carsocialmedia.backend.mapevents.exception.InvalidCursorException}: undecodable
 *       pagination token</li>
 * </ul>
 */
package com.carsocialmedia.backend.mapevents.exception;
