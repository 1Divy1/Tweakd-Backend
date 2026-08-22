package com.tweakdapp.backend.shared.geo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Read-only reference data for {@code cities}.
 */
public interface CityRepository extends JpaRepository<CityEntity, String> {
    List<CityEntity> findByCountry_IdOrderByNameAsc(String countryId);
}