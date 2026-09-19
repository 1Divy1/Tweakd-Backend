package com.tweakdapp.backend.mapevents.internal.repositories;

import com.tweakdapp.backend.mapevents.internal.entities.MapEventEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MapEventRepository extends JpaRepository<MapEventEntity, UUID> {

    /**
     * Loads an event with its category eagerly joined ({@code open-in-view} is off and every caller
     * needs the label). Visibility is <em>not</em> filtered here: an organizer must be able to open
     * their own pending or rejected event, so the service decides who may see what.
     */
    @Query("""
            select e
              from MapEventEntity e
              join fetch e.category
             where e.id = :id
            """)
    Optional<MapEventEntity> findWithCategoryById(@Param("id") UUID id);

    /**
     * Approved, still-running events whose location lies within {@code radiusMetres} of the given
     * centre — the map screen's query.
     *
     * <p>Native because it is a PostGIS query: {@code ST_DWithin} on the {@code geography} column is
     * index-aware, so Postgres uses the GiST index {@code car_events_location_idx}. A bare
     * {@code ST_Distance(...) < x} comparison would force a sequential scan over every event.
     *
     * <p>Ordered by {@code id} purely so {@code limit} truncates deterministically when a radius
     * has more matches than the limit allows — the map renders every pin at its own coordinates
     * regardless of array order, so there is nothing to optimise for beyond stability.
     *
     * <p><strong>The time filter is not redundant with {@code status}.</strong> Nothing sweeps
     * {@code status} automatically (automatic transitions were deferred), so an event nobody marked
     * finished would otherwise sit on the map forever. An event with no {@code ends_at} drops off
     * 24 hours after it starts.
     *
     * <p>The {@code cast(:categoryId as text)} wrapper is required for the {@code is null} branch:
     * without it Postgres cannot infer the parameter's type when it is null.
     */
    @Query(value = """
            select e.id                                                as "id",
                   e.title                                             as "title",
                   e.event_type                                        as "categoryId",
                   c.category                                          as "categoryLabel",
                   st_y(e.location::geometry)                          as "lat",
                   st_x(e.location::geometry)                          as "lng",
                   e.location_name                                     as "locationName",
                   e.cover_image_url                                   as "coverImageKey",
                   e.starts_at                                         as "startsAt",
                   e.ends_at                                           as "endsAt",
                   e.status                                            as "status",
                   e.attendees_count                                   as "attendeesCount",
                   e.attending_cars_count                              as "attendingCarsCount",
                   e.max_participant_capacity                          as "maxParticipantCapacity"
              from car_events e
              join car_event_categories c on c.id = e.event_type
             where e.approval_status = 'accepted'
               and e.status not in ('hidden', 'canceled', 'previous')
               and coalesce(e.ends_at, e.starts_at + interval '24 hours') >= now()
               and st_dwithin(e.location,
                              st_setsrid(st_makepoint(:lng, :lat), 4326)::geography,
                              :radiusMetres)
               and (cast(:categoryId as text) is null or e.event_type = cast(:categoryId as text))
             order by e.id asc
             limit :limit
            """, nativeQuery = true)
    List<MapPinRow> findVisibleNearby(@Param("lat") double lat,
                                      @Param("lng") double lng,
                                      @Param("radiusMetres") double radiusMetres,
                                      @Param("categoryId") String categoryId,
                                      @Param("limit") int limit);

    /**
     * One keyset page of the map's search box: approved events whose title, category label or venue
     * name contains the search text, <strong>nearest to the given centre first</strong>, narrowed
     * to the requested lifecycle phases.
     *
     * <p><strong>The phase is derived, not read.</strong> Nothing sweeps {@code status}
     * automatically — events are created {@code upcoming} and in practice never move to
     * {@code live} — so filtering on the stored column would put a meet that started an hour ago
     * under "upcoming" and a meet that ended last month under "live". {@code effectiveStatus}
     * applies the clock with the map's own rule: an event is over once
     * {@code coalesce(ends_at, starts_at + 24h)} has passed (or it was marked {@code previous});
     * otherwise it is live once it has started (or was marked {@code live}); otherwise upcoming.
     * Canceled and hidden events never match.
     *
     * <p>{@code :pattern} is a ready-made {@code ILIKE} pattern with the caller's own wildcards
     * escaped. The keyset is {@code (distance, id)}; the first page passes
     * {@code afterDistance = -1} so no nullable parameter needs its type inferred.
     */
    @Query(value = """
            select *
              from (select e.id                                        as "id",
                           e.title                                     as "title",
                           e.event_type                                as "categoryId",
                           c.category                                  as "categoryLabel",
                           st_y(e.location::geometry)                  as "lat",
                           st_x(e.location::geometry)                  as "lng",
                           e.location_name                             as "locationName",
                           e.cover_image_url                           as "coverImageKey",
                           e.starts_at                                 as "startsAt",
                           e.ends_at                                   as "endsAt",
                           case
                               when e.status = 'previous'
                                    or coalesce(e.ends_at, e.starts_at + interval '24 hours') < now()
                                   then 'previous'
                               when e.status = 'live' or e.starts_at <= now()
                                   then 'live'
                               else 'upcoming'
                           end                                         as "status",
                           e.attendees_count                           as "attendeesCount",
                           e.attending_cars_count                      as "attendingCarsCount",
                           e.max_participant_capacity                  as "maxParticipantCapacity",
                           st_distance(e.location,
                                       st_setsrid(st_makepoint(:lng, :lat), 4326)::geography)
                                                                       as "distanceMetres"
                      from car_events e
                      join car_event_categories c on c.id = e.event_type
                     where e.approval_status = 'accepted'
                       and e.status not in ('hidden', 'canceled')
                       and (e.title ilike :pattern escape '\\'
                            or c.category ilike :pattern escape '\\'
                            or e.location_name ilike :pattern escape '\\')) m
             where ((:includeLive and m."status" = 'live')
                    or (:includeUpcoming and m."status" = 'upcoming')
                    or (:includePrevious and m."status" = 'previous'))
               and (m."distanceMetres" > :afterDistance
                    or (m."distanceMetres" = :afterDistance and m."id" > :afterId))
             order by m."distanceMetres" asc, m."id" asc
             limit :limit
            """, nativeQuery = true)
    List<SearchPinRow> searchVisible(@Param("pattern") String pattern,
                                     @Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("includeLive") boolean includeLive,
                                     @Param("includeUpcoming") boolean includeUpcoming,
                                     @Param("includePrevious") boolean includePrevious,
                                     @Param("afterDistance") double afterDistance,
                                     @Param("afterId") UUID afterId,
                                     @Param("limit") int limit);

    /**
     * One keyset page of the events a user created or co-organizes, newest first — including their
     * pending and rejected ones, which is the point of the screen.
     *
     * <p><strong>The {@code cast(:cursorCreatedAt as timestamp)} is required, not decorative.</strong>
     * Postgres cannot infer the type of a bare parameter used only as {@code ? is null}, and fails
     * the whole statement with "could not determine data type of parameter". Since the first page
     * is exactly the case that passes a null cursor, dropping the cast breaks every first request.
     * The same applies to the {@code cast(:status as string)} filters in the attendee and
     * participant repositories.
     */
    @Query("""
            select e
              from MapEventEntity e
              join fetch e.category
             where (e.createdBy = :userId
                    or exists (select 1
                                 from MapEventOrganizerEntity o
                                where o.eventId = e.id
                                  and o.individualOrganizerId = :userId))
               and (cast(:cursorCreatedAt as timestamp) is null
                    or e.createdAt < :cursorCreatedAt
                    or (e.createdAt = :cursorCreatedAt and e.id < :cursorId))
             order by e.createdAt desc, e.id desc
            """)
    List<MapEventEntity> findMine(@Param("userId") UUID userId,
                                  @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                  @Param("cursorId") UUID cursorId,
                                  Limit limit);

    /**
     * One keyset page of events in a given approval state, oldest first — the admin review queue,
     * so the longest-waiting submission is handled first.
     */
    @Query("""
            select e
              from MapEventEntity e
              join fetch e.category
             where e.approvalStatus = :approvalStatus
               and (cast(:cursorCreatedAt as timestamp) is null
                    or e.createdAt > :cursorCreatedAt
                    or (e.createdAt = :cursorCreatedAt and e.id > :cursorId))
             order by e.createdAt asc, e.id asc
            """)
    List<MapEventEntity> findByApprovalStatus(@Param("approvalStatus") String approvalStatus,
                                              @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                              @Param("cursorId") UUID cursorId,
                                              Limit limit);

    /** Badge count for the admin dashboard's "events awaiting review". */
    long countByApprovalStatus(String approvalStatus);

    /** Row shape of {@link #findVisibleNearby}; assembled into a map pin by the service. */
    interface MapPinRow {
        UUID getId();
        String getTitle();
        String getCategoryId();
        String getCategoryLabel();
        double getLat();
        double getLng();
        String getLocationName();
        String getCoverImageKey();
        Instant getStartsAt();
        Instant getEndsAt();
        String getStatus();
        int getAttendeesCount();
        int getAttendingCarsCount();
        Integer getMaxParticipantCapacity();
    }

    /**
     * Row shape of {@link #searchVisible}: a map pin whose {@code status} is the clock-derived
     * phase, plus the distance its keyset is ordered by.
     */
    interface SearchPinRow extends MapPinRow {
        double getDistanceMetres();
    }
}
