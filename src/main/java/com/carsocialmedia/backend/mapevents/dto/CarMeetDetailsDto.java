package com.carsocialmedia.backend.mapevents.dto;

import java.time.Instant;

/**
 * The extra detail a {@code car_meet} event carries beyond the shared event fields. Null on a
 * {@link MapEventDto} whose category is not a car meet.
 *
 * <p>One of these per subcategory as more are added.
 *
 * @param registrationDeadline after this instant no further cars may be entered
 */
public record CarMeetDetailsDto(
        Instant registrationDeadline
) {}
