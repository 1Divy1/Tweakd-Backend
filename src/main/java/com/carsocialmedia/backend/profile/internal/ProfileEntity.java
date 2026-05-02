package com.carsocialmedia.backend.profile.internal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
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
    private boolean requiresOnboarding;
}
