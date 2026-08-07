package com.carsocialmedia.backend.business.internal.entities;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A real business with a physical location — the entity behind a map pin and a business profile
 * page. Not tied to any {@code profiles} row: businesses are a separate identity type managed from
 * a business dashboard.
 *
 * <p>Rows are only visible to the app when {@code activeStatus = "active"} and
 * {@code verificationStatus = "verified"}; see {@link #ACTIVE} / {@link #VERIFIED}.
 */
@Entity
@DynamicUpdate
@Table(name = "business_accounts")
@Getter
@Setter
public class BusinessAccountEntity {

    /** {@code business_account_active_status_options.id} for a live business. */
    public static final String ACTIVE = "active";

    /** {@code business_account_verification_status_options.id} for a moderator-approved business. */
    public static final String VERIFIED = "verified";

    @Id
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type")
    private BusinessTypeOptionEntity type;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "website_url")
    private String websiteUrl;

    @Column(name = "address", nullable = false)
    private String address;

    /**
     * The business's physical position. Indexed with GiST
     * ({@code business_accounts_location_idx}) — this is what radius search filters on.
     */
    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "location", columnDefinition = "geography(Point,4326)", nullable = false)
    private Point location;

    /**
     * FK to {@code cities.id} — a table the profile module owns, so it is held as a plain id here
     * rather than as an association. Display names are resolved via {@code ProfileService}.
     */
    @Column(name = "city", nullable = false)
    private String cityId;

    /** FK to {@code business_account_verification_status_options.id}: pending / verified / rejected. */
    @Column(name = "verification_status", nullable = false)
    private String verificationStatus;

    /** FK to {@code business_account_active_status_options.id}: active / suspended / deleted. */
    @Column(name = "active_status", nullable = false)
    private String activeStatus;

    /** Set when a moderator approves the business; {@code null} while pending or rejected. */
    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "description")
    private String description;

    /** Maintained from reviews, not by this module. */
    @Column(name = "average_rating", nullable = false)
    private BigDecimal averageRating;

    /** Maintained from reviews, not by this module. */
    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    /** Maintained by follows, not by this module. */
    @Column(name = "follower_count", nullable = false)
    private int followerCount;

    /**
     * IANA zone id (e.g. {@code Europe/Bucharest}) that {@code business_hours} times are expressed
     * in. Per-business rather than app-wide so the app can operate across countries.
     */
    @Column(name = "timezone", nullable = false)
    private String timezone;

    /** DB-managed: DEFAULT now() in Supabase. */
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    /** Whether this business may be shown in the app at all. */
    public boolean isVisible() {
        return ACTIVE.equals(activeStatus) && VERIFIED.equals(verificationStatus);
    }
}
