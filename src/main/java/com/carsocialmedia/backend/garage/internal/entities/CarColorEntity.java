package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "car_color_options")
@Getter
@Setter
public class CarColorEntity {
    @Id
    private String id;
    private String name;

    @Column(name = "color_code")
    private String colorCode;
}