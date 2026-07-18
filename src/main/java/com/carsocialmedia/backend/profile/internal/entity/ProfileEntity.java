package com.carsocialmedia.backend.profile.internal.entity;

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

    @Column(name = "bio", length = 500)
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

    @Column(name = "requires_onboarding")
    private boolean requiresOnboarding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private CityEntity city;

    @Column(name = "discovery_radius_km")
    private Integer discoveryRadiusKm;

    /** UI language code (e.g. {@code "en"}, {@code "ro"}); FK to {@code app_language_options.id}. */
    @Column(name = "app_language")
    private String appLanguage;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "realtime_location", columnDefinition = "geography")
    private Point realtimeLocation;

    /** Set by a moderator ban; enforced by {@code BannedUserInterceptor}. */
    @Column(name = "is_banned")
    private boolean isBanned;

    /** Temp-ban expiry; {@code null} while banned means permanent. */
    @Column(name = "banned_until")
    private java.time.Instant bannedUntil;

    /** DB-managed: backfilled from auth signup; DEFAULT now() for new rows. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private java.time.Instant createdAt;
}
