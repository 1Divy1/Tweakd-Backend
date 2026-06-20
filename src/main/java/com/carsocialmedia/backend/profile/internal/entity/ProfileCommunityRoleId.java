package com.carsocialmedia.backend.profile.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@code public.profile_community_roles_junction}:
 * ({@code profile_id}, {@code role_id}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProfileCommunityRoleId implements Serializable {

    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "role_id")
    private String roleId;
}