package com.carsocialmedia.backend.business;

import com.carsocialmedia.backend.business.dto.BusinessDto;
import com.carsocialmedia.backend.business.dto.BusinessMapPinDto;
import com.carsocialmedia.backend.business.dto.BusinessRefDto;
import com.carsocialmedia.backend.business.dto.BusinessTypeOptionDto;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read API over business accounts. Every method returns only businesses that are active and
 * verified; pending, rejected, suspended and deleted businesses are invisible to the app.
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
     * @throws com.carsocialmedia.backend.business.exception.InvalidSearchAreaException if any bound is violated
     */
    List<BusinessMapPinDto> findNearby(double lat, double lng, double radiusKm, String typeId, int limit);

    /**
     * The full profile of one business, including its weekly opening hours.
     *
     * @throws com.carsocialmedia.backend.business.exception.BusinessNotFoundException if no such
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
}
