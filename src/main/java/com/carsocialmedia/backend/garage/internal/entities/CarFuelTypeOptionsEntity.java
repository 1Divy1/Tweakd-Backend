package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "car_fuel_type_options")
@Getter
@Setter
public class CarFuelTypeOptionsEntity {

    @Id
    private String id;

    @Column(name = "name")
    private String name;
}
