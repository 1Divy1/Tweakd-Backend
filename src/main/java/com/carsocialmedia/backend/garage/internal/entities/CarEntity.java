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

/**
 * A car owned by a user, stored in a specific garage.
 *
 * Contains detailed specifications (year, horsepower, torque, etc.) and references to
 * lookup tables for brand, model, drivetrain, color, and mileage unit. The createdAt
 * timestamp is DB-managed and read-only from the ORM side.
 */
@Entity
@DynamicUpdate
@Table(name = "cars")
@Getter
@Setter
public class CarEntity {

    /** The car ID (UUID, PK). */
    @Id
    private UUID id;

    /** The garage this car belongs to (required, FK to garages.id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garage_id")
    private GarageEntity garage;

    /** The car brand (required, FK to car_brands.id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id")
    private CarBrandEntity brand;

    /** The car model (required, FK to car_models.id, must belong to the brand). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id")
    private CarModelEntity model;

    /** The drivetrain type (required, FK to car_drivetrains.id, e.g., FWD, RWD). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drivetrain_id")
    private CarDrivetrainEntity drivetrain;

    /** The paint/body color (required, FK to car_colors.id). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "color_id")
    private CarColorEntity color;

    /** The mileage unit (required, FK to car_distance_units.id, e.g., km, miles). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mileage_unit_id")
    private CarDistanceUnitEntity mileageUnit;

    /** The year the car was manufactured (1900-2100). */
    private int year;

    /** Engine horsepower. */
    private int horsepower;

    /** Engine torque in Nm (can be 0 for electric cars). */
    private int torque;

    /** Car weight in kilograms. */
    private int weight;

    /** Engine displacement in liters. */
    @Column(name = "engine_displacement")
    private float engineDisplacement;

    /** 0-100 km/h acceleration time in seconds (nullable). */
    @Column(name = "zero_to_one_hundred")
    private Float zeroToOneHundred;

    /** Internal chassis/body code (e.g., F80, F82 for BMW). */
    @Column(name = "chassis_code")
    private String chassisCode;

    /** Internal engine code (e.g., S65B40, M340i). */
    @Column(name = "engine_code")
    private String engineCode;

    /** URL to the car's cover/hero image. */
    @Column(name = "cover_image_url")
    private String coverImageUrl;

    /** When the car was added to the garage (managed by Supabase, read-only). */
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}