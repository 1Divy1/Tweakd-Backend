package com.carsocialmedia.backend.garage.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "garages")
@Getter
@Setter
public class GarageEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id")
    private UUID ownerId;

    private String name;

    // DB-managed: DEFAULT now() in Supabase. Omitting from INSERT/UPDATE lets the default fire.
    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}
