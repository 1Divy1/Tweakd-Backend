package com.tweakdapp.backend.shared.geo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Read-only reference data for {@code countries}.
 */
public interface CountryRepository extends JpaRepository<CountryEntity, String> {
    List<CountryEntity> findAllByOrderByNameAsc();
}