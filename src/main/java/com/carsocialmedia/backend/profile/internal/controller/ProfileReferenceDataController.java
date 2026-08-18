package com.carsocialmedia.backend.profile.internal.controller;

import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.shared.geo.CityDto;
import com.carsocialmedia.backend.shared.geo.CountryDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only reference data used to populate the onboarding wizard's pickers
 * (countries, cities). Mirrors the garage module's {@code ReferenceDataController}.
 */
@RestController
@RequestMapping("/api/v1/profile/reference")
class ProfileReferenceDataController {

    private final ProfileService profileService;

    ProfileReferenceDataController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/countries")
    public List<CountryDto> getCountries() {
        return profileService.listCountries();
    }

    @GetMapping("/countries/{countryId}/cities")
    public List<CityDto> getCitiesByCountry(@PathVariable String countryId) {
        return profileService.listCities(countryId);
    }
}