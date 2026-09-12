package com.tweakdapp.backend.business;

import com.tweakdapp.backend.business.dto.AdminBusinessDto;
import com.tweakdapp.backend.business.dto.AdminBusinessPageDto;
import com.tweakdapp.backend.business.dto.BusinessDto;
import com.tweakdapp.backend.business.dto.BusinessMapPinDto;
import com.tweakdapp.backend.business.dto.BusinessRefDto;
import com.tweakdapp.backend.business.dto.BusinessTypeOptionDto;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * API over business accounts. Every <em>app-facing</em> method returns only businesses that are
 * active and verified; pending, rejected, suspended and deleted businesses are invisible to the
 * app. The admin section at the bottom is the one exception — it is called by the {@code admin}
 * module behind the {@code VERIFY_BUSINESSES} capability, and exists so verification is a button
 * rather than a hand-written UPDATE.
 */
public interface BusinessService {

    /**
     * Businesses within {@code radiusKm} of a centre point, nearest first — the query behind the
     * virtual map.
     *
     * <p>The caller supplies the centre: the app should use the user's realtime location when they
     * have granted location permission, and otherwise fall back to the coordinates of their home
     * city (already available client-side from {@code ProfileService.listCities}).
     *
     * @param lat      centre latitude, -90..90
     * @param lng      centre longitude, -180..180
     * @param radiusKm search radius in kilometres, 0 &lt; radiusKm &le; 500
     * @param typeId   optional business type filter (e.g. {@code tuning_shop}); {@code null} = all types
     * @param limit    maximum pins to return, 1..500
     * @throws com.tweakdapp.backend.business.exception.InvalidSearchAreaException if any bound is violated
     */
    List<BusinessMapPinDto> findNearby(double lat, double lng, double radiusKm, String typeId, int limit);

    /**
     * The full profile of one business, including its weekly opening hours.
     *
     * @throws com.tweakdapp.backend.business.exception.BusinessNotFoundException if no such
     *         business exists <em>or</em> it is not active and verified — the two are deliberately
     *         indistinguishable to the caller, so a suspended business cannot be probed for.
     */
    BusinessDto getBusiness(UUID businessId);

    /** All selectable business categories (reference data), alphabetically by label. */
    List<BusinessTypeOptionDto> listTypes();

    /**
     * Batch-resolves business ids to name + logo, for callers that only need to render a credit or
     * a link — the business counterpart of {@code ProfileService.findByIds}.
     *
     * <p>Ids that do not resolve to an active, verified business are <strong>dropped</strong>, in
     * keeping with this module's rule that hidden businesses are never exposed. Callers must
     * therefore tolerate a result smaller than the input.
     *
     * @param ids the business ids to resolve; empty input returns an empty list
     */
    List<BusinessRefDto> findBusinessRefsByIds(Collection<UUID> ids);

    /**
     * Active, verified businesses whose name starts with {@code prefix} (case-insensitive),
     * alphabetical, capped at 20 — the business counterpart of
     * {@code ProfileService.searchByUsername}.
     *
     * @param prefix search text; blank or {@code null} returns an empty list
     */
    List<BusinessRefDto> searchByName(String prefix);

    // ===================== Admin (called by the admin module) =====================
    //
    // The only writes this module has. They exist because a business is invisible to the app until
    // someone verifies it, and that decision had no home outside the SQL editor. They do not make
    // the module writable in general: nothing here creates, edits or deletes a business, and the
    // app-facing reads above still refuse to return anything that is not active and verified.

    /**
     * One keyset page of the review queue, <strong>oldest submission first</strong> so nothing waits
     * indefinitely. No visibility filter — seeing pending and rejected rows is the point.
     *
     * @param verificationStatus optional filter: {@code pending} (the default queue),
     *                           {@code verified} or {@code rejected}; {@code null} for all
     * @param activeStatus       optional filter: {@code active}, {@code suspended} or
     *                           {@code deleted}; {@code null} for all
     * @param cursor             opaque token from the previous page, or {@code null} for the first
     * @param size               page size, clamped to 1..100
     * @throws com.tweakdapp.backend.business.exception.InvalidBusinessStatusException if a status
     *         filter is not one of the known values
     */
    AdminBusinessPageDto listForReview(String verificationStatus, String activeStatus, String cursor, int size);

    /**
     * Full detail of one business whatever its status — the reviewer's panel.
     *
     * @throws com.tweakdapp.backend.business.exception.BusinessNotFoundException if no such id
     */
    AdminBusinessDto getForReview(UUID businessId);

    /** How many businesses are waiting for verification — the dashboard badge. */
    long countPendingVerification();

    /**
     * Approves a business: {@code verification_status = 'verified'}, {@code verified_at} stamped,
     * any previous rejection reason cleared. Idempotent. It becomes visible to the app only if its
     * active status is also {@code active}, which is the default for a new row.
     *
     * @param reviewerId staff auth user id, recorded for audit
     */
    AdminBusinessDto verify(UUID businessId, UUID reviewerId);

    /**
     * Turns a business down with a reason the operator can act on. The row stays; nothing is
     * deleted, and re-verifying later is one call.
     *
     * @throws com.tweakdapp.backend.business.exception.InvalidBusinessStatusException if the reason
     *         is blank — a rejection nobody can act on is worse than none
     */
    AdminBusinessDto reject(UUID businessId, String reason, UUID reviewerId);

    /**
     * Sets the active status: {@code active} puts a verified business back on the map,
     * {@code suspended} takes it off without touching its verification. Idempotent.
     *
     * @throws com.tweakdapp.backend.business.exception.InvalidBusinessStatusException if the status
     *         is unknown, or is {@code deleted} — soft-deleting a business is not a dashboard
     *         action, precisely because nothing in the app can undo it
     */
    AdminBusinessDto setActiveStatus(UUID businessId, String activeStatus, UUID reviewerId);
}
