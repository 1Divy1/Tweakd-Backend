package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "car_distance_units")
@Getter
@Setter
public class CarDistanceUnitEntity {
    @Id
    private String id;
    private String name;
}