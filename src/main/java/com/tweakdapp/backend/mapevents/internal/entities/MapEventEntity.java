package com.tweakdapp.backend.mapevents.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/**
 * A car event shown as a pin on the map — the root row every other table in this module hangs off.
 *
 * <p>Two independent state columns, deliberately not merged:
 * <ul>
 *   <li>{@link #approvalStatus} — the admin gate. Nothing is visible on the map until it is
 *       {@link #APPROVAL_ACCEPTED}.</li>
 *   <li>{@link #status} — where the event is in its own life. Stored, never derived, and nothing
 *       transitions it automatically (deferred by the owner); organizers move it themselves.</li>
 * </ul>
 */
@Entity
@DynamicUpdate
@Table(name = "car_events")
@Getter
@Setter
public class MapEventEntity {

    // --- approval_status: car_event_approval_status_options.id ---

    /** Awaiting an admin decision. The default for every newly created event. */
    public static final String APPROVAL_PENDING = "pending";

    /** Admin-approved: the event is live on the map and locked against organizer edits. */
    public static final String APPROVAL_ACCEPTED = "accepted";

    /** Admin-rejected; {@link #rejectionReason} says why. Editing it resubmits as pending. */
    public static final String APPROVAL_REJECTED = "rejected";

    // --- status: car_event_status_options.id ---

    /** Has not started yet. DB default for a new event. */
    public static final String STATUS_UPCOMING = "upcoming";

    /** Currently running. */
    public static final String STATUS_LIVE = "live";

    /** Finished — set when an organizer marks the event finished. */
    public static final String STATUS_PREVIOUS = "previous";

    /** Pulled from the map without being cancelled (admin housekeeping). */
    public static final String STATUS_HIDDEN = "hidden";

    /** Called off by an organizer. The event page survives so attendees can see it was cancelled. */
    public static final String STATUS_CANCELED = "canceled";

    @Id
    private UUID id;

    /** FK to {@code car_event_categories.id} — {@code car_meet} today, more later. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_type")
    private MapEventCategoryEntity category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    /** Human-readable place ("Parking lot, Iulius Mall") shown under the title. */
    @Column(name = "location_name", nullable = false)
    private String locationName;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    /** Optional: not every event announces an end time. */
    @Column(name = "ends_at")
    private Instant endsAt;

    /**
     * Where the pin sits. Indexed with GiST ({@code car_events_location_idx}) — this is what the
     * radius search filters on.
     */
    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "location", columnDefinition = "geography(Point,4326)", nullable = false)
    private Point location;

    /**
     * R2 object <em>key</em> in the {@code MAP_EVENTS} bucket, not a URL — resolved on read via
     * {@code StorageService.publicUrl}. Null only in the window between creating the event and the
     * client attaching the uploaded cover.
     */
    @Column(name = "cover_image_url")
    private String coverImageKey;

    /** FK to {@code car_event_status_options.id}; see the STATUS_* constants. */
    @Column(name = "status", nullable = false)
    private String status;

    /** FK to {@code car_event_approval_status_options.id}; see the APPROVAL_* constants. */
    @Column(name = "approval_status", nullable = false)
    private String approvalStatus;

    /** Set when an admin rejects; cleared when the organizer edits and resubmits. */
    @Column(name = "rejection_reason")
    private String rejectionReason;

    /**
     * Whether a car entered into this event needs an organizer's approval before it counts as a
     * participant. When false, registrations are accepted immediately.
     */
    @Column(name = "requires_participant_approval", nullable = false)
    private boolean requiresParticipantApproval;

    /** Optional cap on accepted cars ({@link #attendingCarsCount}); {@code null} means no limit. */
    @Column(name = "max_participant_capacity")
    private Integer maxParticipantCapacity;

    /** The single user who created the event. Immutable — it decides who may delete it. */
    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    /** Trigger-owned ({@code trg_update_car_event_attendees_count}); never written here. */
    @Column(name = "attendees_count", insertable = false, updatable = false)
    private int attendeesCount;

    /** Trigger-owned ({@code trg_update_car_event_attending_cars_count}); never written here. */
    @Column(name = "attending_cars_count", insertable = false, updatable = false)
    private int attendingCarsCount;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Whether an admin has approved this event, i.e. whether the app may show it at all. */
    public boolean isApproved() {
        return APPROVAL_ACCEPTED.equals(approvalStatus);
    }

    /**
     * Whether organizers may still edit. Approval locks an event so it cannot be approved as one
     * thing and quietly become another.
     */
    public boolean isEditable() {
        return APPROVAL_PENDING.equals(approvalStatus) || APPROVAL_REJECTED.equals(approvalStatus);
    }

    /**
     * Whether the event has run its course, at the given instant — no RSVP, no registrations, no
     * organizer actions beyond deletion.
     *
     * <p>Both halves matter: an organizer may mark an event finished early, and because nothing
     * sweeps {@link #status} automatically, an event whose {@code ends_at} has passed must read as
     * over even while its stored status still says {@code upcoming}.
     */
    public boolean hasFinished(Instant now) {
        return STATUS_PREVIOUS.equals(status)
                || STATUS_CANCELED.equals(status)
                || (endsAt != null && !now.isBefore(endsAt));
    }
}
