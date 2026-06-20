package com.carsocialmedia.backend.profile.internal.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Join row linking a profile to a community role. Owned by the profile module;
 * write semantics are replace-all (delete this profile's rows, then insert the new set).
 */
@Entity
@Table(name = "profile_community_roles_junction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProfileCommunityRoleEntity {

    @EmbeddedId
    private ProfileCommunityRoleId id;
}