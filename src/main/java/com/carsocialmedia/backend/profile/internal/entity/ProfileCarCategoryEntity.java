package com.carsocialmedia.backend.profile.internal.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Join row linking a profile to a favorite car category. Owned by the profile module;
 * write semantics are replace-all (delete this profile's rows, then insert the new set).
 */
@Entity
@Table(name = "profile_car_categories_junction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfileCarCategoryEntity {

    @EmbeddedId
    private ProfileCarCategoryId id;
}