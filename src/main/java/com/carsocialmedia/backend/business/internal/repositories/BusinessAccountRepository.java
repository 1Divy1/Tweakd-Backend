package com.carsocialmedia.backend.business.internal.repositories;

import com.carsocialmedia.backend.business.internal.entities.BusinessAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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
     * centre, nearest first.
     *
     * <p>Native because it is a PostGIS query: {@code ST_DWithin} on the {@code geography} column is
     * what lets Postgres use the GiST index {@code business_accounts_location_idx} — it is an index
     * -aware operator, unlike a bare {@code ST_Distance(...) < x} comparison, which would force a
     * sequential scan over every business.
     *
     * <p>{@code ST_Distance} on {@code geography} returns metres along the spheroid, so the
     * kilometre conversion is a plain division.
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
                   b.timezone                                          as "timezone",
                   st_distance(b.location,
                               st_setsrid(st_makepoint(:lng, :lat), 4326)::geography)
                       / 1000.0                                        as "distanceKm"
              from business_accounts b
              join business_type_options t on t.id = b.type
             where b.active_status = 'active'
               and b.verification_status = 'verified'
               and st_dwithin(b.location,
                              st_setsrid(st_makepoint(:lng, :lat), 4326)::geography,
                              :radiusMetres)
               and (cast(:typeId as text) is null or b.type = cast(:typeId as text))
             order by "distanceKm" asc, b.id asc
             limit :limit
            """, nativeQuery = true)
    List<MapPinRow> findVisibleNearby(@Param("lat") double lat,
                                      @Param("lng") double lng,
                                      @Param("radiusMetres") double radiusMetres,
                                      @Param("typeId") String typeId,
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
        double getDistanceKm();
    }
}
