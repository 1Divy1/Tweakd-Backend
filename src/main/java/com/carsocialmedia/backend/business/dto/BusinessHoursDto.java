package com.carsocialmedia.backend.business.dto;

/**
 * One weekday's opening hours.
 *
 * <p>Times are wall-clock in the business's own timezone (see {@link BusinessDto#timezone()}) and
 * are formatted as {@code "HH:mm"} rather than as date-times, because they carry no date. A
 * {@code closingHour} less than or equal to {@code openingHour} means the shift runs past midnight
 * into the following day (e.g. a 24h car wash: {@code 22:00 → 06:00}).
 *
 * @param weekday      ISO-8601 day of week: 1 = Monday … 7 = Sunday
 * @param isClosed     whether the business is closed all day; when true both times are {@code null}
 * @param openingHour  opening time as {@code "HH:mm"}, or {@code null} when closed
 * @param closingHour  closing time as {@code "HH:mm"}, or {@code null} when closed
 * @param notes        free-text note for the day (e.g. "appointment only"), or {@code null}
 */
public record BusinessHoursDto(
        int weekday,
        boolean isClosed,
        String openingHour,
        String closingHour,
        String notes
) {}
