package com.tweakdapp.backend.shared.geo;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/**
 * A predefined city a user can select during onboarding. Reference data managed by
 * Supabase. Each city belongs to a country and carries a {@code geography(Point,4326)}
 * location used for (future) proximity-based features.
 */
@Entity
@DynamicUpdate
@Table(name = "cities")
@Getter
@Setter
public class CityEntity {

    @Id
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "region", nullable = false)
    private String region;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country")
    private CountryEntity country;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "location", columnDefinition = "geography(Point,4326)", nullable = false)
    private Point location;

    public CityDto toDto() {
        return new CityDto(
                id,
                name,
                region,
                country.getId(),
                GeoSupport.latOf(location),
                GeoSupport.lngOf(location)
        );
    }
}