package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "cars")
@Getter
@Setter
public class CarEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garage_id")
    private GarageEntity garage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id")
    private CarBrandEntity brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id")
    private CarModelEntity model;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drivetrain_id")
    private CarDrivetrainEntity drivetrain;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "color_id")
    private CarColorEntity color;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mileage_unit_id")
    private CarDistanceUnitEntity mileageUnit;

    private int year;
    private int horsepower;
    private int torque;
    private int weight;

    @Column(name = "engine_displacement")
    private float engineDisplacement;

    @Column(name = "zero_to_one_hundred")
    private Float zeroToOneHundred;

    @Column(name = "chassis_code")
    private String chassisCode;

    @Column(name = "engine_code")
    private String engineCode;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    // DB-managed: DEFAULT now() in Supabase.
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}