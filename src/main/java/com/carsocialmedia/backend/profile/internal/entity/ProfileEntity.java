package com.carsocialmedia.backend.profile.internal.entity;

import com.carsocialmedia.backend.profile.dto.ProfileDto;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.dto.PublicProfileDto;
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

import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "profiles")
@Getter
@Setter
public class ProfileEntity {

    @Id
    private UUID id;

    @Column(name = "role")
    private String role;

    @Column(name = "name")
    private String name;

    @Column(name = "username", unique = true)
    private String username;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "bio", length = 2000)
    private String bio;

    @Column(name = "external_link")
    private String externalLink;

    @Column(name = "followers_count")
    private int followersCount;

    @Column(name = "following_count")
    private int followingCount;

    @Column(name = "is_verified")
    private boolean isVerified;

    @Column(name = "is_business")
    private boolean isBusiness;

    @Column(name = "is_private")
    private boolean isPrivate;

    @Column(name = "requires_onboarding")
    private boolean requiresOnboarding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private CityEntity city;

    @Column(name = "discovery_radius_km")
    private Integer discoveryRadiusKm;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "realtime_location", columnDefinition = "geography")
    private Point realtimeLocation;

    public ProfileDto toDto() {
        return new ProfileDto(
                id, role, name, username, avatarUrl, bio,
                externalLink, followersCount, followingCount,
                isVerified, isBusiness, isPrivate, requiresOnboarding,
                city.getId(),
                discoveryRadiusKm
        );
    }

    public ProfileSearchResultDto toSearchResultDto() {
        return new ProfileSearchResultDto(id, username, avatarUrl);
    }

    public PublicProfileDto toPublicDto() {
        return new PublicProfileDto(
                id, name, username, avatarUrl, bio,
                externalLink, followersCount, followingCount,
                isVerified, isBusiness, isPrivate
        );
    }
}
