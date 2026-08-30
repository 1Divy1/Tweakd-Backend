package com.tweakdapp.backend.shared.geo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@DynamicUpdate
@Table(name = "countries")
@Getter
@Setter
public class CountryEntity {
    @Id
    private String id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    public CountryDto toDto() {
        return new CountryDto(id, name);
    }
}
