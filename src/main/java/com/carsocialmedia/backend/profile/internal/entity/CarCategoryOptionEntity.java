package com.carsocialmedia.backend.profile.internal.entity;

import com.carsocialmedia.backend.profile.dto.CarCategoryDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A car category a user can mark as a favorite during onboarding.
 *
 * Reference data table ({@code car_category_options}). Read-only lookup managed by
 * Supabase; joined to a profile through {@code profile_car_categories_junction}.
 */
@Entity
@Table(name = "car_category_options")
@Getter
@Setter
public class CarCategoryOptionEntity {

    @Id
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    public CarCategoryDto toDto() {
        return new CarCategoryDto(id, name);
    }
}