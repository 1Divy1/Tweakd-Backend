package com.carsocialmedia.backend.profile.dto;

import java.util.List;

/**
 * Replace-all selection of a user's favorite car categories. An empty list clears
 * the selection.
 */
public record CategorySelectionRequest(List<String> categoryIds) {
        public CategorySelectionRequest {
                if (categoryIds == null) {
                        categoryIds = List.of();
                }
        }
}