package com.carsocialmedia.backend.profile.dto;

import java.util.List;

/**
 * Replace-all selection of a user's community roles. An empty list clears the
 * selection.
 */
public record RoleSelectionRequest(List<String> roleIds) {
        public RoleSelectionRequest {
                if (roleIds == null) {
                        roleIds = List.of();
                }
        }
}