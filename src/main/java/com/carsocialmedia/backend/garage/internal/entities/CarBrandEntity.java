package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "car_brands")
@Getter
@Setter
public class CarBrandEntity {
    @Id
    private UUID id;
    private String name;
}