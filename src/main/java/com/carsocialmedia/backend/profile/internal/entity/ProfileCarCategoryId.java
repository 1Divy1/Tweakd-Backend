package com.carsocialmedia.backend.profile.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@code public.profile_car_categories_junction}:
 * ({@code profile_id}, {@code category_id}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProfileCarCategoryId implements Serializable {

    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "category_id")
    private String categoryId;
}