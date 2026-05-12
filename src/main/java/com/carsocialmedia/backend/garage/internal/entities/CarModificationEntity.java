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
@Table(name = "car_modifications")
@Getter
@Setter
public class CarModificationEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "car_id")
    private CarEntity car;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private CarModCategoryEntity category;

    private String title;
    private String description;

    @Column(name = "before_image_url")
    private String beforeImageUrl;

    @Column(name = "after_image_url")
    private String afterImageUrl;

    @Column(name = "installation_date")
    private Instant installationDate;

    private Float price;

    @Column(name = "is_price_public")
    private boolean isPricePublic;

    @Column(name = "mileage_at_install")
    private Integer mileageAtInstall;

    // DB-managed: DEFAULT now() in Supabase.
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}
