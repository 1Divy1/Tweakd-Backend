package com.carsocialmedia.backend.profile.internal.entity;

import com.carsocialmedia.backend.profile.dto.CommunityRoleDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A community role a user can pick during onboarding (e.g. enthusiast, mechanic).
 *
 * Reference data table ({@code community_role_options}). Read-only lookup managed by
 * Supabase; joined to a profile through {@code profile_community_roles_junction}.
 */
@Entity
@Table(name = "community_role_options")
@Getter
@Setter
public class CommunityRoleOptionEntity {

    @Id
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    public CommunityRoleDto toDto() {
        return new CommunityRoleDto(id, name);
    }
}