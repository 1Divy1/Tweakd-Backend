package com.carsocialmedia.backend.profile;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileService {
    ProfileDto getProfile(String userId);
    ProfileDto completeOnboarding(String userId, OnboardingRequest request);
    ProfileDto setPrivacy(String userId, boolean isPrivate);
    PublicProfileDto getPublicProfileByUsername(String username);
    List<ProfileSearchResultDto> searchByUsername(String prefix);

    /**
     * Lookup helpers for sibling modules. Returning DTOs (not entities) keeps
     * profile internals private while still letting other modules resolve
     * usernames or hydrate user lists.
     */
    Optional<UUID> findIdByUsername(String username);
    boolean isPrivate(UUID userId);
    List<ProfileSearchResultDto> findByIds(Collection<UUID> ids);
}
