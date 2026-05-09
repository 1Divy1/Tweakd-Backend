package com.carsocialmedia.backend.profile.internal;

import com.carsocialmedia.backend.profile.ProfileDto;
import com.carsocialmedia.backend.profile.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.PublicProfileDto;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "profiles")
@Getter
@Setter
class ProfileEntity {
    @Id
    private UUID id;
    private String role;
    private String name;
    private String username;
    private String avatarUrl;
    private String bio;
    private String externalLink;
    private int followersCount;
    private int followingCount;
    private boolean isVerified;
    private boolean isBusiness;
    private boolean isPrivate;
    private boolean requiresOnboarding;

    ProfileDto toDto() {
        return new ProfileDto(
                id, role, name, username, avatarUrl, bio,
                externalLink, followersCount, followingCount,
                isVerified, isBusiness, isPrivate, requiresOnboarding
        );
    }

    ProfileSearchResultDto toSearchResultDto() {
        return new ProfileSearchResultDto(id, username, avatarUrl);
    }

    PublicProfileDto toPublicDto() {
        return new PublicProfileDto(
                id, name, username, avatarUrl, bio,
                externalLink, followersCount, followingCount,
                isVerified, isBusiness, isPrivate
        );
    }
}
