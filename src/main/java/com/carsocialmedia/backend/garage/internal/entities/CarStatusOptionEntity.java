package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "car_status_options")
@Getter
@Setter
public class CarStatusOptionEntity {
    @Id
    private String id;
    private String type;
}
