package com.tweakdapp.backend.business.internal.repositories;

import com.tweakdapp.backend.business.internal.entities.BusinessAccountEntity;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BusinessAccountRepository extends JpaRepository<BusinessAccountEntity, UUID> {

    /**
     * Loads a business only if it is active and verified, with its type eagerly joined (the caller
     * needs the label and {@code open-in-view} is off). Returning empty for a hidden business — as
     * opposed to loading it and checking afterwards — keeps the "not found" and "not visible" cases
     * indistinguishable at the API.
     */
    @Query("""
            select b
              from BusinessAccountEntity b
              join fetch b.type
             where b.id = :id
               and b.activeStatus = 'active'
               and b.verificationStatus = 'verified'
            """)
    Optional<BusinessAccountEntity> findVisibleById(@Param("id") UUID id);

    /**
     * Active, verified businesses whose location lies within {@code radiusMetres} of the given
     * centre.
     *
     * <p>Native because it is a PostGIS query: {@code ST_DWithin} on the {@code geography} column is
     * what lets Postgres use the GiST index {@code business_accounts_location_idx} — it is an index
     * -aware operator, unlike a bare {@code ST_Distance(...) < x} comparison, which would force a
     * sequential scan over every business.
     *
     * <p>Ordered by {@code id} purely so {@code limit} truncates deterministically when a radius
     * has more matches than the limit allows — the map renders every pin at its own coordinates
     * regardless of array order, so there is nothing to optimise for beyond stability.
     *
     * <p>The {@code cast(:typeId as text)} wrapper is required for the {@code is null} branch:
     * without it Postgres cannot infer the parameter's type when it is null.
     */
    @Query(value = """
            select b.id                                                as "id",
                   b.name                                              as "name",
                   b.type                                              as "typeId",
                   t.type                                              as "typeLabel",
                   st_y(b.location::geometry)                          as "lat",
                   st_x(b.location::geometry)                          as "lng",
                   b.logo_url                                          as "logoUrl",
                   b.average_rating                                    as "averageRating",
                   b.review_count                                      as "reviewCount",
                   b.timezone                                          as "timezone"
              from business_accounts b
              join business_type_options t on t.id = b.type
             where b.active_status = 'active'
               and b.verification_status = 'verified'
               and st_dwithin(b.location,
                              st_setsrid(st_makepoint(:lng, :lat), 4326)::geography,
                              :radiusMetres)
               and (cast(:typeId as text) is null or b.type = cast(:typeId as text))
             order by b.id asc
             limit :limit
            """, nativeQuery = true)
    List<MapPinRow> findVisibleNearby(@Param("lat") double lat,
                                      @Param("lng") double lng,
                                      @Param("radiusMetres") double radiusMetres,
                                      @Param("typeId") String typeId,
                                      @Param("limit") int limit);

    /**
     * One keyset page of the map's search box: active, verified businesses whose name or type label
     * contains the search text, <strong>nearest to the given centre first</strong>.
     *
     * <p>{@code :pattern} is a ready-made {@code ILIKE} pattern ({@code %text%}) with the caller's
     * own {@code %}, {@code _} and {@code \} already escaped, so a query of {@code "100%"} matches
     * that literal text rather than everything.
     *
     * <p>The keyset is {@code (distance, id)}: the trailing id makes the order total, so two
     * businesses at exactly the same distance can neither repeat nor go missing across pages. The
     * first page passes {@code afterDistance = -1}, which every real distance beats — this avoids a
     * nullable double/uuid pair, whose types Postgres cannot infer.
     *
     * <p>No spatial index helps here — the filter is the text match, not a radius — so this is a
     * scan of the visible businesses. Fine at today's table size; {@code pg_trgm} is the upgrade
     * path once it is not.
     */
    @Query(value = """
            select *
              from (select b.id                                        as "id",
                           b.name                                      as "name",
                           b.type                                      as "typeId",
                           t.type                                      as "typeLabel",
                           st_y(b.location::geometry)                  as "lat",
                           st_x(b.location::geometry)                  as "lng",
                           b.logo_url                                  as "logoUrl",
                           b.average_rating                            as "averageRating",
                           b.review_count                              as "reviewCount",
                           b.timezone                                  as "timezone",
                           st_distance(b.location,
                                       st_setsrid(st_makepoint(:lng, :lat), 4326)::geography)
                                                                       as "distanceMetres"
                      from business_accounts b
                      join business_type_options t on t.id = b.type
                     where b.active_status = 'active'
                       and b.verification_status = 'verified'
                       and (b.name ilike :pattern escape '\\'
                            or t.type ilike :pattern escape '\\')) m
             where m."distanceMetres" > :afterDistance
                or (m."distanceMetres" = :afterDistance and m."id" > :afterId)
             order by m."distanceMetres" asc, m."id" asc
             limit :limit
            """, nativeQuery = true)
    List<SearchPinRow> searchVisible(@Param("pattern") String pattern,
                                     @Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("afterDistance") double afterDistance,
                                     @Param("afterId") UUID afterId,
                                     @Param("limit") int limit);

    /**
     * Name + logo key for a batch of ids, active and verified only. Hidden and unknown ids simply
     * do not come back, so a caller can never learn that a suspended business exists.
     */
    @Query("""
            select b
              from BusinessAccountEntity b
             where b.id in :ids
               and b.activeStatus = 'active'
               and b.verificationStatus = 'verified'
            """)
    List<BusinessAccountEntity> findVisibleByIds(@Param("ids") Collection<UUID> ids);

    /**
     * Active, verified businesses whose name starts with {@code prefix} (case-insensitive),
     * alphabetical. {@code pageable} caps the result size — the caller passes a size-20, unsorted
     * first page.
     */
    @Query("""
            select b
              from BusinessAccountEntity b
             where b.activeStatus = 'active'
               and b.verificationStatus = 'verified'
               and lower(b.name) like lower(concat(:prefix, '%'))
             order by b.name asc
            """)
    List<BusinessAccountEntity> searchVisibleByNameStartingWith(@Param("prefix") String prefix, Pageable pageable);

    // ------------------------------------------------------------------
    // Admin review queue (no visibility filter — that is the whole point)
    // ------------------------------------------------------------------

    /**
     * One keyset page of the review queue, oldest first, optionally narrowed by verification and/or
     * active status. The type is fetch-joined because the list shows its label and
     * {@code open-in-view} is off.
     *
     * <p>{@code cast(:x as text)} on every optional filter is what lets one query serve all four
     * filter combinations: without it Hibernate cannot infer a null parameter's type. The keyset
     * predicate is spelled out rather than using a row comparison so it stays index-friendly.
     */
    @Query("""
            select b
              from BusinessAccountEntity b
              join fetch b.type
             where (cast(:verificationStatus as string) is null
                    or b.verificationStatus = cast(:verificationStatus as string))
               and (cast(:activeStatus as string) is null
                    or b.activeStatus = cast(:activeStatus as string))
               and (cast(:afterCreatedAt as timestamp) is null
                    or b.createdAt > cast(:afterCreatedAt as timestamp)
                    or (b.createdAt = cast(:afterCreatedAt as timestamp) and b.id > :afterId))
             order by b.createdAt asc, b.id asc
            """)
    List<BusinessAccountEntity> findForReview(@Param("verificationStatus") String verificationStatus,
                                              @Param("activeStatus") String activeStatus,
                                              @Param("afterCreatedAt") Instant afterCreatedAt,
                                              @Param("afterId") UUID afterId,
                                              Limit limit);

    /** One business whatever its status, type fetch-joined — the reviewer's detail read. */
    @Query("""
            select b
              from BusinessAccountEntity b
              join fetch b.type
             where b.id = :id
            """)
    Optional<BusinessAccountEntity> findForReviewById(@Param("id") UUID id);

    /** Backs the dashboard's pending badge; served by {@code business_accounts_pending_review_idx}. */
    long countByVerificationStatus(String verificationStatus);

    /** Row shape of {@link #findVisibleNearby}; assembled into a map pin by the service. */
    interface MapPinRow {
        UUID getId();
        String getName();
        String getTypeId();
        String getTypeLabel();
        double getLat();
        double getLng();
        String getLogoUrl();
        BigDecimal getAverageRating();
        int getReviewCount();
        String getTimezone();
    }

    /** Row shape of {@link #searchVisible}: a map pin plus the distance its keyset is ordered by. */
    interface SearchPinRow extends MapPinRow {
        double getDistanceMetres();
    }
}
