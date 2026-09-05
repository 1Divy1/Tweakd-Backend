package com.tweakdapp.backend.garage.internal.repositories;

import com.tweakdapp.backend.garage.internal.entities.CarShareLinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link CarShareLinkEntity}.
 *
 * <p>Reads come in two shapes and the difference matters: the owner-facing lookups ask for the
 * <em>live</em> link ({@code revokedAt is null}), while {@link #findByCode} deliberately returns
 * revoked rows too — a code that has been retired must answer 410 Gone rather than 404, because a
 * printed sticker deserves "this build is no longer shared" and not "no such page".
 */
public interface CarShareLinkRepository extends JpaRepository<CarShareLinkEntity, UUID> {

    /** The car's current link, if it has one. At most one row exists — the partial unique index. */
    @Query("""
            select l
              from CarShareLinkEntity l
             where l.car.id = :carId
               and l.revokedAt is null
            """)
    Optional<CarShareLinkEntity> findLiveByCarId(@Param("carId") UUID carId);

    /**
     * Resolves a public code, revoked rows included, with the car and everything the public
     * projection reads already joined so nothing lazy-loads outside the transaction.
     */
    @Query("""
            select l
              from CarShareLinkEntity l
              join fetch l.car c
              join fetch c.garage
             where l.code = :code
            """)
    Optional<CarShareLinkEntity> findByCode(@Param("code") String code);

    /** Whether a generated candidate code is already taken. See {@code ShareCodeGenerator}. */
    boolean existsByCode(String code);

    /** The owner's live links. Backs a future "my shared cars" screen; unused in v1. */
    List<CarShareLinkEntity> findByOwnerIdAndRevokedAtIsNull(UUID ownerId);

    /**
     * Counts one view against a link.
     *
     * <p>A bulk update rather than a loaded-entity write: views are concurrent by nature and the
     * increment has to happen in the database, or two simultaneous scans of the same sticker both
     * read the same number and both write it back plus one.
     *
     * <p>Exactly one of {@code views} / {@code scans} is 1 and the other 0 — the caller has already
     * decided which bucket the request falls into ({@code ?s=qr} or not).
     */
    @Modifying
    @Query("""
            update CarShareLinkEntity l
               set l.viewCount = l.viewCount + :views,
                   l.qrScanCount = l.qrScanCount + :scans,
                   l.lastViewedAt = :now
             where l.id = :id
            """)
    int recordView(@Param("id") UUID id,
                   @Param("views") int views,
                   @Param("scans") int scans,
                   @Param("now") Instant now);
}
