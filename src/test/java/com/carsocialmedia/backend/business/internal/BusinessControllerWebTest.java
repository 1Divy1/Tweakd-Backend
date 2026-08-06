package com.carsocialmedia.backend.business.internal;

import com.carsocialmedia.backend.business.BusinessService;
import com.carsocialmedia.backend.business.dto.BusinessDto;
import com.carsocialmedia.backend.business.dto.BusinessHoursDto;
import com.carsocialmedia.backend.business.dto.BusinessMapPinDto;
import com.carsocialmedia.backend.business.dto.BusinessTypeOptionDto;
import com.carsocialmedia.backend.business.exception.BusinessNotFoundException;
import com.carsocialmedia.backend.business.exception.InvalidSearchAreaException;
import com.carsocialmedia.backend.testsupport.AppWebMvcTest;
import com.carsocialmedia.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST surface of {@link BusinessController}: the auth requirement, the exact snake_case JSON
 * the mobile app parses (the map screen builds Mapbox markers straight from these field names, so
 * a rename here is a silent client break), query-parameter defaults, and the exception→status
 * mapping.
 */
@AppWebMvcTest(BusinessController.class)
class BusinessControllerWebTest {

    private static final UUID BUSINESS_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BusinessService businessService;

    private static BusinessMapPinDto pin() {
        return new BusinessMapPinDto(
                BUSINESS_ID, "Willy Wash", "car_wash", "Car wash",
                46.7874479284246, 23.6308604799099,
                "https://cdn.example/logo.png",
                4.5, 12, true, 1.8342);
    }

    // ---- auth ---------------------------------------------------------------

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/businesses/nearby?lat=46.77&lng=23.62"))
                .andExpect(status().isUnauthorized());
    }

    // ---- GET /nearby --------------------------------------------------------

    @Test
    void nearbyReturnsTheMapPinShape() throws Exception {
        when(businessService.findNearby(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of(pin()));

        mockMvc.perform(get("/api/v1/businesses/nearby?lat=46.7712&lng=23.6236&radius_km=25")
                        .with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(BUSINESS_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Willy Wash"))
                .andExpect(jsonPath("$[0].type_id").value("car_wash"))
                .andExpect(jsonPath("$[0].type_label").value("Car wash"))
                .andExpect(jsonPath("$[0].lat").value(46.7874479284246))
                .andExpect(jsonPath("$[0].lng").value(23.6308604799099))
                .andExpect(jsonPath("$[0].logo_url").value("https://cdn.example/logo.png"))
                .andExpect(jsonPath("$[0].average_rating").value(4.5))
                .andExpect(jsonPath("$[0].review_count").value(12))
                .andExpect(jsonPath("$[0].is_open_now").value(true))
                .andExpect(jsonPath("$[0].distance_km").value(1.8342));
    }

    @Test
    void nearbyAppliesRadiusAndLimitDefaultsAndPassesTheTypeFilter() throws Exception {
        when(businessService.findNearby(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/businesses/nearby?lat=46.7712&lng=23.6236&type=tuning_shop")
                        .with(TestJwts.user()))
                .andExpect(status().isOk());

        // Documented defaults: radius_km=25, limit=200.
        verify(businessService).findNearby(46.7712, 23.6236, 25.0, "tuning_shop", 200);
    }

    @Test
    void nearbyRequiresLatAndLng() throws Exception {
        mockMvc.perform(get("/api/v1/businesses/nearby?lat=46.7712").with(TestJwts.user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nearbyOutOfBoundsIsABadRequestWithTheSharedErrorShape() throws Exception {
        when(businessService.findNearby(anyDouble(), anyDouble(), anyDouble(), any(), anyInt()))
                .thenThrow(new InvalidSearchAreaException("radius_km must be greater than 0 and at most 500.0"));

        mockMvc.perform(get("/api/v1/businesses/nearby?lat=46.7712&lng=23.6236&radius_km=9000")
                        .with(TestJwts.user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("radius_km must be greater than 0 and at most 500.0"));
    }

    // ---- GET /{businessId} --------------------------------------------------

    @Test
    void getBusinessReturnsTheProfileShapeIncludingHours() throws Exception {
        when(businessService.getBusiness(BUSINESS_ID)).thenReturn(new BusinessDto(
                BUSINESS_ID, "Willy Wash", "car_wash", "Car wash",
                "Hand wash and detailing.", "https://cdn.example/logo.png",
                "Str. Memorandumului 28", "cluj-napoca", "Cluj-Napoca",
                46.7874479284246, 23.6308604799099,
                "+40712345678", "hello@willywash.ro", "https://willywash.ro",
                4.5, 12, 340, "Europe/Bucharest", true,
                List.of(new BusinessHoursDto(1, false, "09:00", "18:00", null),
                        new BusinessHoursDto(7, true, null, null, "Closed Sundays")),
                Instant.parse("2026-08-05T14:57:09Z"),
                Instant.parse("2026-08-01T10:00:00Z")));

        mockMvc.perform(get("/api/v1/businesses/{id}", BUSINESS_ID).with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BUSINESS_ID.toString()))
                .andExpect(jsonPath("$.type_id").value("car_wash"))
                .andExpect(jsonPath("$.type_label").value("Car wash"))
                .andExpect(jsonPath("$.description").value("Hand wash and detailing."))
                .andExpect(jsonPath("$.address").value("Str. Memorandumului 28"))
                // The slug and the display name are both exposed; the app should render city_name.
                .andExpect(jsonPath("$.city_id").value("cluj-napoca"))
                .andExpect(jsonPath("$.city_name").value("Cluj-Napoca"))
                .andExpect(jsonPath("$.phone_number").value("+40712345678"))
                .andExpect(jsonPath("$.website_url").value("https://willywash.ro"))
                .andExpect(jsonPath("$.follower_count").value(340))
                .andExpect(jsonPath("$.timezone").value("Europe/Bucharest"))
                .andExpect(jsonPath("$.is_open_now").value(true))
                .andExpect(jsonPath("$.hours[0].weekday").value(1))
                .andExpect(jsonPath("$.hours[0].is_closed").value(false))
                .andExpect(jsonPath("$.hours[0].opening_hour").value("09:00"))
                .andExpect(jsonPath("$.hours[0].closing_hour").value("18:00"))
                .andExpect(jsonPath("$.hours[1].is_closed").value(true))
                .andExpect(jsonPath("$.hours[1].opening_hour").doesNotExist())
                .andExpect(jsonPath("$.hours[1].notes").value("Closed Sundays"))
                .andExpect(jsonPath("$.verified_at").exists());
    }

    @Test
    void unknownOrHiddenBusinessIsANotFound() throws Exception {
        when(businessService.getBusiness(BUSINESS_ID))
                .thenThrow(new BusinessNotFoundException(BUSINESS_ID));

        mockMvc.perform(get("/api/v1/businesses/{id}", BUSINESS_ID).with(TestJwts.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void aMalformedBusinessIdIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/businesses/{id}", "not-a-uuid").with(TestJwts.user()))
                .andExpect(status().isBadRequest());
    }

    // ---- GET /types ---------------------------------------------------------

    @Test
    void listTypesReturnsReferenceDataAndIsNotShadowedByTheIdRoute() throws Exception {
        when(businessService.listTypes())
                .thenReturn(List.of(new BusinessTypeOptionDto("car_wash", "Car wash")));

        mockMvc.perform(get("/api/v1/businesses/types").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("car_wash"))
                .andExpect(jsonPath("$[0].label").value("Car wash"));

        verify(businessService).listTypes();
    }
}