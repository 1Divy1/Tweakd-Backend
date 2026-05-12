package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "car_mod_categories")
@Getter
@Setter
public class CarModCategoryEntity {
    @Id
    private String id;

    @Column(name = "mod_name")
    private String modName;
}