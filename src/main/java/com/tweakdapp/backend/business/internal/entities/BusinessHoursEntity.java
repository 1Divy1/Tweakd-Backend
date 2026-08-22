package com.tweakdapp.backend.business.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One weekday's opening hours for a business. At most one row per (business, weekday), enforced by
 * {@code business_hours_business_weekday_key}.
 *
 * <p>Times are wall-clock in the owning business's {@code timezone} and carry no date. A
 * {@code closingHour} at or before {@code openingHour} means the shift runs past midnight into the
 * next day; equal times mean the business is open around the clock. A closed day has
 * {@code isClosed = true} and both times {@code null}, enforced by
 * {@code business_hours_times_present}.
 */
@Entity
@DynamicUpdate
@Table(name = "business_hours")
@Getter
@Setter
public class BusinessHoursEntity {

    @Id
    private UUID id;

    /** FK to {@code business_accounts.id}; held as a plain id because hours are always loaded by business. */
    @Column(name = "business_id", nullable = false)
    private UUID businessId;

    /** ISO-8601 day of week: 1 = Monday … 7 = Sunday, matching {@link java.time.DayOfWeek#getValue()}. */
    @Column(name = "weekday", nullable = false)
    private short weekday;

    @Column(name = "opening_hour")
    private LocalTime openingHour;

    @Column(name = "closing_hour")
    private LocalTime closingHour;

    /** Closed all day; both times are {@code null}. */
    @Column(name = "is_closed", nullable = false)
    private boolean isClosed;

    /** Free-text note for the day, e.g. "appointment only". */
    @Column(name = "notes")
    private String notes;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
