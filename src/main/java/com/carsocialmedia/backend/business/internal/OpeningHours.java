package com.carsocialmedia.backend.business.internal;

import com.carsocialmedia.backend.business.internal.entities.BusinessHoursEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;

/**
 * Derives "is this business open right now?" from its weekly schedule.
 *
 * <p>The answer is computed per request rather than stored, because a stored flag is wrong the
 * moment the clock passes a closing time. The cost is a comparison over at most seven rows.
 *
 * <p>Two details make this less trivial than it looks:
 * <ul>
 *   <li><strong>Timezone</strong> — hours are wall-clock in the business's own zone, so "now" must
 *       be converted into that zone before comparing. Using the server's zone would drift by hours
 *       once the app operates outside Romania, and would break twice a year at DST boundaries even
 *       inside it.</li>
 *   <li><strong>Shifts past midnight</strong> — a {@code closingHour} at or before
 *       {@code openingHour} (e.g. {@code 22:00 → 06:00}) means the shift spills into the next day.
 *       Such a shift must therefore also be checked against <em>yesterday's</em> row: at 02:00 on
 *       Tuesday the business is open because of Monday's entry, not Tuesday's.</li>
 * </ul>
 */
final class OpeningHours {

    private static final Logger log = LoggerFactory.getLogger(OpeningHours.class);

    private OpeningHours() {
    }

    /**
     * Whether {@code week} says the business is open at {@code now}.
     *
     * @param week     the business's hours rows; may be partial or empty
     * @param timezone IANA zone id from {@code business_accounts.timezone}
     * @param now      the instant to evaluate
     * @return {@code false} if no row covers the moment, or if the schedule is empty
     */
    static boolean isOpenAt(Collection<BusinessHoursEntity> week, String timezone, Instant now) {
        if (week == null || week.isEmpty()) {
            return false;
        }

        ZonedDateTime local = now.atZone(zoneOf(timezone));
        LocalTime time = local.toLocalTime();
        int today = local.getDayOfWeek().getValue();
        int yesterday = local.getDayOfWeek().minus(1).getValue();

        for (BusinessHoursEntity day : week) {
            if (day.isClosed() || day.getOpeningHour() == null || day.getClosingHour() == null) {
                continue;
            }
            boolean overnight = !day.getClosingHour().isAfter(day.getOpeningHour());

            if (day.getWeekday() == today) {
                // A same-day shift ends today; an overnight one runs to midnight and beyond.
                boolean open = overnight
                        ? !time.isBefore(day.getOpeningHour())
                        : !time.isBefore(day.getOpeningHour()) && time.isBefore(day.getClosingHour());
                if (open) {
                    return true;
                }
            }
            // Yesterday's overnight shift may still be running in the small hours of today.
            if (day.getWeekday() == yesterday && overnight && time.isBefore(day.getClosingHour())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves the business's zone, falling back to UTC if the stored id is unusable. A bad zone id
     * should be impossible — {@code business_accounts_timezone_valid} rejects one at write time —
     * but a map screen degrading to a wrong "open" badge is far better than it failing to load.
     */
    private static ZoneId zoneOf(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException | NullPointerException e) {
            log.warn("Unrecognised business timezone '{}', falling back to UTC", timezone);
            return ZoneId.of("UTC");
        }
    }
}
