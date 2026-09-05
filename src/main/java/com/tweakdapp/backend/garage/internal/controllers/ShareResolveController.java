package com.tweakdapp.backend.garage.internal.controllers;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarShareResolutionDto;
import com.tweakdapp.backend.garage.dto.ShareSource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where a scanned QR or a tapped share link lands when the app is installed.
 *
 * <p>The OS hands the Universal Link / App Link to the app, which calls this to turn the opaque
 * code into a car id and then opens its own {@code /garage/cars/{carId}} screen. Authenticated on
 * purpose: an installed, signed-in user should get the native car page, not a second rendering of
 * the public one.
 */
@RestController
@RequestMapping("/api/v1/garage/share")
public class ShareResolveController {

    private final GarageService garageService;

    public ShareResolveController(GarageService garageService) {
        this.garageService = garageService;
    }

    /**
     * @param code the code from the incoming URL, as-is — lowercase, dashed and O-for-0 variants
     *             all resolve
     * @param source the {@code ?s=} tag carried over from the link the user opened, so an in-app
     *               open from a QR scan counts as a scan just like the web page does
     */
    @GetMapping("/resolve/{code}")
    public CarShareResolutionDto resolve(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable String code,
                                         @RequestParam(name = "s", required = false) String source) {
        return garageService.resolveShareCode(jwt.getSubject(), code, ShareSource.fromTag(source));
    }
}
