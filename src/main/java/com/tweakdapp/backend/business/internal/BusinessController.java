package com.tweakdapp.backend.business.internal;

import com.tweakdapp.backend.business.BusinessService;
import com.tweakdapp.backend.business.dto.BusinessDto;
import com.tweakdapp.backend.business.dto.BusinessMapPinDto;
import com.tweakdapp.backend.business.dto.BusinessSearchPageDto;
import com.tweakdapp.backend.business.dto.BusinessTypeOptionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/businesses")
class BusinessController {

    private final BusinessService businessService;

    BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    /**
     * Businesses near a point, nearest first — the map screen's query. The response is a flat list
     * of pins ready to hand to Mapbox; tapping one leads to {@link #getBusiness(UUID)}.
     *
     * <p>{@code lat}/{@code lng} are the map's centre and are chosen by the client: the user's
     * realtime location when location permission was granted, otherwise the coordinates of their
     * home city (already available from the profile module's city reference data).
     */
    @GetMapping("/nearby")
    public List<BusinessMapPinDto> findNearby(@RequestParam double lat,
                                             @RequestParam double lng,
                                             @RequestParam(name = "radius_km", defaultValue = "25") double radiusKm,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(defaultValue = "200") int limit) {
        return businessService.findNearby(lat, lng, radiusKm, type, limit);
    }

    /**
     * The map's search box: businesses whose name or type contains {@code q}, nearest to
     * {@code lat}/{@code lng} (the map's centre) first, one keyset page at a time. Send the same
     * centre with every page of one search.
     */
    @GetMapping("/search")
    public BusinessSearchPageDto search(@RequestParam String q,
                                        @RequestParam double lat,
                                        @RequestParam double lng,
                                        @RequestParam(required = false) String cursor,
                                        @RequestParam(defaultValue = "20") int size) {
        return businessService.search(q, lat, lng, cursor, size);
    }

    /** A business's full profile, including its weekly opening hours. */
    @GetMapping("/{businessId}")
    public BusinessDto getBusiness(@PathVariable UUID businessId) {
        return businessService.getBusiness(businessId);
    }

    /** Business categories — powers the map's type filter and per-type marker icons. */
    @GetMapping("/types")
    public List<BusinessTypeOptionDto> listTypes() {
        return businessService.listTypes();
    }
}
