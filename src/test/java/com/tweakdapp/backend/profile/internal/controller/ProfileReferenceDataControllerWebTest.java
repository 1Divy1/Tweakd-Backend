package com.tweakdapp.backend.profile.internal.controller;

import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.shared.geo.CityDto;
import com.tweakdapp.backend.shared.geo.CountryDto;
import com.tweakdapp.backend.testsupport.AppWebMvcTest;
import com.tweakdapp.backend.testsupport.TestJwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The read-only reference-data surface of {@link ProfileReferenceDataController}: auth requirement,
 * delegation of the path variable, and snake_case DTO shapes (notably {@code CityDto.country_id} and
 * its exposed lat/lng).
 */
@AppWebMvcTest(ProfileReferenceDataController.class)
class ProfileReferenceDataControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileService profileService;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/profile/reference/countries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCountriesReturnsList() throws Exception {
        when(profileService.listCountries()).thenReturn(List.of(new CountryDto("RO", "Romania")));

        mockMvc.perform(get("/api/v1/profile/reference/countries").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("RO"))
                .andExpect(jsonPath("$[0].name").value("Romania"));
    }

    @Test
    void getCitiesByCountryDelegatesThePathVariableAndReturnsSnakeCaseCoordinates() throws Exception {
        when(profileService.listCities("RO"))
                .thenReturn(List.of(new CityDto("cluj", "Cluj-Napoca", "Cluj", "RO", 46.77, 23.59)));

        mockMvc.perform(get("/api/v1/profile/reference/countries/{c}/cities", "RO").with(TestJwts.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cluj"))
                .andExpect(jsonPath("$[0].country_id").value("RO"))
                .andExpect(jsonPath("$[0].lat").value(46.77))
                .andExpect(jsonPath("$[0].lng").value(23.59));

        verify(profileService).listCities("RO");
    }
}
