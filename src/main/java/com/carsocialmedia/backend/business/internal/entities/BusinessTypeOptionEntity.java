package com.carsocialmedia.backend.business.internal.entities;

import com.carsocialmedia.backend.business.dto.BusinessTypeOptionDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A business category (auto repair shop, car wash, tuning shop, …). Reference data seeded in
 * Supabase; the app maps the {@code id} to a map marker icon and shows {@code type} as the label.
 */
@Entity
@Table(name = "business_type_options")
@Getter
@Setter
public class BusinessTypeOptionEntity {

    @Id
    private String id;

    /** Display label, e.g. {@code "Tuning shop"}. */
    @Column(name = "type", nullable = false)
    private String type;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public BusinessTypeOptionDto toDto() {
        return new BusinessTypeOptionDto(id, type);
    }
}
