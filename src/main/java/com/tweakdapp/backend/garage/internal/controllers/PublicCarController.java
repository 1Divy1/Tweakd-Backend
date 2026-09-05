package com.tweakdapp.backend.garage.internal.controllers;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.PublicCarDto;
import com.tweakdapp.backend.garage.dto.ShareSource;
import com.tweakdapp.backend.garage.internal.share.CrawlerUserAgents;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one unauthenticated route in the application: the car behind a public share link.
 *
 * <p>Called by the website's Cloudflare Pages Function, not by browsers — the function fetches this
 * at the edge, injects the {@code og:*} tags a crawler needs and the JSON the page hydrates from.
 * That indirection is why the backend needs no CORS configuration: no browser ever calls it
 * cross-origin.
 *
 * <p>Everything about this endpoint assumes hostile traffic. The response is a hand-written
 * projection rather than the in-app car DTO; there is no caller identity to leak; and the
 * cache headers exist because one viral link is otherwise one database round trip per scan against
 * a two-connection pool.
 */
@RestController
@RequestMapping("/public/v1/cars")
public class PublicCarController {

    private final GarageService garageService;

    public PublicCarController(GarageService garageService) {
        this.garageService = garageService;
    }

    /**
     * @param code the share code from the URL
     * @param source the {@code ?s=} tag: {@code qr} for a scan of the printed sticker, anything
     *               else (or nothing) for a tapped link
     * @param userAgent used for one decision only — whether this visit is counted. A WhatsApp
     *                  unfurl of a link sent to a group of forty produces forty fetches before a
     *                  human has tapped anything; counting those would make the owner's number
     *                  meaningless. The response is identical either way.
     */
    @GetMapping("/{code}")
    public ResponseEntity<PublicCarDto> getPublicCar(
            @PathVariable String code,
            @RequestParam(name = "s", required = false) String source,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {

        PublicCarDto car = garageService.getPublicCar(
                code,
                ShareSource.fromTag(source),
                !CrawlerUserAgents.matches(userAgent));

        return ResponseEntity.ok()
                // Short on the browser, longer at the edge: a build sheet changes rarely, and the
                // five minutes Cloudflare holds it is what absorbs a link that suddenly does well.
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=60, s-maxage=300")
                .body(car);
    }
}
