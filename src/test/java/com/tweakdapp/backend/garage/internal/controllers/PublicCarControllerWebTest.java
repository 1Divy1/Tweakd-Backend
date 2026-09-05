package com.tweakdapp.backend.garage.internal.controllers;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.PublicBadgeDto;
import com.tweakdapp.backend.garage.dto.PublicCarDto;
import com.tweakdapp.backend.garage.dto.PublicCarModificationDto;
import com.tweakdapp.backend.garage.dto.PublicCarOwnerDto;
import com.tweakdapp.backend.garage.dto.PublicMediaDto;
import com.tweakdapp.backend.garage.dto.ShareSource;
import com.tweakdapp.backend.garage.exception.ShareLinkGoneException;
import com.tweakdapp.backend.garage.exception.ShareLinkNotFoundException;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The application's one unauthenticated route.
 *
 * <p>The first test is the load-bearing one: it proves the {@code /public/**} matcher in
 * {@link com.tweakdapp.backend.shared.security.SecurityConfig} actually lets an anonymous request
 * through, which is the whole feature — a stranger scanning a sticker has no JWT and never will.
 * The rest pin what that request gets back: never a UUID, never a storage key, a 410 that a crawler
 * can tell apart from a 404, and cache headers, because one link that does well is otherwise one
 * database round trip per scan against a two-connection pool.
 */
@AppWebMvcTest(PublicCarController.class)
class PublicCarControllerWebTest {

    private static final String CODE = "7KQ3M9XA2F";
    private static final String BASE = "/public/v1/cars/";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarageService garageService;

    private static PublicCarDto page() {
        return new PublicCarDto(
                CODE, "https://web.tweakdapp.com/c/" + CODE,
                "BMW", "M5", 2022,
                "F90", "F90", "S63",
                635, 750, 1900, 4.4f, 3.3f,
                "AWD", "Frozen Black", "#111111", "Petrol", "Daily Driver",
                12_000, "km",
                "Bought it after ten years of saving.",
                "https://media.tweakdapp.com/cars/cover.jpg",
                List.of("https://media.tweakdapp.com/cars/g1.jpg"),
                List.of(new PublicCarModificationDto(
                        "Suspension", "H&R Coilovers", "Dropped 30mm.",
                        List.of(new PublicMediaDto("https://media.tweakdapp.com/mods/after.jpg", "image", "after")),
                        Instant.parse("2026-04-01T10:00:00Z"), 1200, "EUR", 9_000)),
                new PublicCarOwnerDto("dave", "Dave", "https://avatars.tweakdapp.com/dave.jpg",
                        true, 420, List.of(new PublicBadgeDto("Pioneer", "https://assets.tweakd.app/pioneer.svg"))),
                Instant.parse("2026-09-04T12:00:00Z"));
    }

    // ---- the point of the whole feature -------------------------------------

    /** No Authorization header, no session, no cookie — and a 200. */
    @Test
    void thePublicPageIsServedWithNoAuthenticationAtAll() throws Exception {
        when(garageService.getPublicCar(eq(CODE), any(), anyBoolean())).thenReturn(page());

        mockMvc.perform(get(BASE + CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brand_name").value("BMW"))
                .andExpect(jsonPath("$.model_name").value("M5"));
    }

    // ---- the wire shape -----------------------------------------------------

    @Test
    void theBuildAndOwnerCardAreSerialisedInSnakeCase() throws Exception {
        when(garageService.getPublicCar(eq(CODE), any(), anyBoolean())).thenReturn(page());

        mockMvc.perform(get(BASE + CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(CODE))
                .andExpect(jsonPath("$.url").value("https://web.tweakdapp.com/c/" + CODE))
                .andExpect(jsonPath("$.horsepower").value(635))
                .andExpect(jsonPath("$.zero_to_one_hundred").value(3.3))
                .andExpect(jsonPath("$.mileage_unit_name").value("km"))
                .andExpect(jsonPath("$.cover_image_url").value("https://media.tweakdapp.com/cars/cover.jpg"))
                .andExpect(jsonPath("$.gallery_urls[0]").value("https://media.tweakdapp.com/cars/g1.jpg"))
                .andExpect(jsonPath("$.modifications[0].category_name").value("Suspension"))
                .andExpect(jsonPath("$.modifications[0].price_currency").value("EUR"))
                .andExpect(jsonPath("$.modifications[0].media[0].phase").value("after"))
                .andExpect(jsonPath("$.owner.username").value("dave"))
                .andExpect(jsonPath("$.owner.reputation_score").value(420))
                .andExpect(jsonPath("$.owner.badges[0].name").value("Pioneer"))
                .andExpect(jsonPath("$.shared_at").exists());
    }

    /** Nothing internal is on the open internet: no ids, no R2 keys, no plate. */
    @Test
    void theResponseCarriesNoInternalIdentifiers() throws Exception {
        when(garageService.getPublicCar(eq(CODE), any(), anyBoolean())).thenReturn(page());

        mockMvc.perform(get(BASE + CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.car_id").doesNotExist())
                .andExpect(jsonPath("$.garage_id").doesNotExist())
                .andExpect(jsonPath("$.license_plate").doesNotExist())
                .andExpect(jsonPath("$.owner.id").doesNotExist())
                .andExpect(jsonPath("$.modifications[0].id").doesNotExist())
                .andExpect(jsonPath("$.modifications[0].media[0].key").doesNotExist())
                .andExpect(jsonPath("$.cover_image.key").doesNotExist());
    }

    // ---- caching and counting ----------------------------------------------

    /** Sixty seconds in the browser, five minutes at Cloudflare — see the controller's comment. */
    @Test
    void theResponseIsCacheable() throws Exception {
        when(garageService.getPublicCar(eq(CODE), any(), anyBoolean())).thenReturn(page());

        mockMvc.perform(get(BASE + CODE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=60, s-maxage=300"));
    }

    /** A WhatsApp unfurl is served in full, and is not counted. */
    @Test
    void aCrawlerIsServedButNotCounted() throws Exception {
        when(garageService.getPublicCar(eq(CODE), any(), anyBoolean())).thenReturn(page());

        mockMvc.perform(get(BASE + CODE).header(HttpHeaders.USER_AGENT, "WhatsApp/2.23.20.0 A"))
                .andExpect(status().isOk());
        verify(garageService).getPublicCar(eq(CODE), eq(ShareSource.LINK), eq(false));

        mockMvc.perform(get(BASE + CODE).header(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) Mobile/15E148 Safari/604.1"))
                .andExpect(status().isOk());
        verify(garageService).getPublicCar(eq(CODE), eq(ShareSource.LINK), eq(true));
    }

    @Test
    void theQrSourceTagIsPassedThrough() throws Exception {
        when(garageService.getPublicCar(eq(CODE), any(), anyBoolean())).thenReturn(page());

        mockMvc.perform(get(BASE + CODE + "?s=qr")).andExpect(status().isOk());

        verify(garageService).getPublicCar(eq(CODE), eq(ShareSource.QR), eq(true));
    }

    // ---- dead links ---------------------------------------------------------

    /**
     * The distinction exists for crawlers alone — a 410 is dropped from an index, a 404 is retried
     * — and the website renders the same "no longer shared" screen for both.
     */
    @Test
    void aPausedOrRevokedLinkIsGoneAndAnUnknownOneIsNotFound() throws Exception {
        when(garageService.getPublicCar(eq(CODE), any(), anyBoolean()))
                .thenThrow(new ShareLinkGoneException());
        mockMvc.perform(get(BASE + CODE))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("This build is no longer shared on Tweakd."));

        when(garageService.getPublicCar(eq("ZZZZZZZZZZ"), any(), anyBoolean()))
                .thenThrow(new ShareLinkNotFoundException());
        mockMvc.perform(get(BASE + "ZZZZZZZZZZ")).andExpect(status().isNotFound());
    }
}
