package com.tweakdapp.backend.profile.internal;

import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.profile.dto.ProfileDto;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.profile.dto.PublicProfileDto;
import com.tweakdapp.backend.profile.internal.entity.ProfileEntity;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Single place that turns a {@link ProfileEntity} into its outward DTOs. Centralises avatar-URL
 * resolution so every avatar-exposing path ({@link ProfileDto}, {@link PublicProfileDto},
 * {@link ProfileSearchResultDto}, moderation snapshots) speaks the same rule:
 *
 * <ul>
 *   <li>blank (null/empty) → returned as-is (preserves the "no avatar" value the row holds);</li>
 *   <li>starts with {@code http} → returned as-is (legacy Google-photo URLs);</li>
 *   <li>otherwise → treated as an R2 object key and resolved to a public URL via
 *       {@link StorageService#publicUrl}.</li>
 * </ul>
 */
@Component
class ProfileDtoMapper {

    private final StorageService storageService;

    ProfileDtoMapper(StorageService storageService) {
        this.storageService = storageService;
    }

    /** Resolves a stored {@code avatar_url} value to the URL clients should render. */
    String resolveAvatarUrl(String stored) {
        if (stored == null || stored.isBlank()) {
            return stored;
        }
        if (stored.startsWith("http")) {
            return stored;
        }
        return storageService.publicUrl(StorageBucket.AVATARS, stored);
    }

    /**
     * @param badges the profile owner's unlocked badges. Passed in rather than looked up here: this
     *               is a mapper, and hiding a query behind it would make one fire per row the day
     *               someone maps a list.
     */
    ProfileDto toDto(ProfileEntity p, List<UserBadgeDto> badges) {
        return new ProfileDto(
                p.getId(), p.getRole(), p.getName(), p.getUsername(),
                resolveAvatarUrl(p.getAvatarUrl()), p.getBio(),
                p.getExternalLink(), p.getFollowersCount(), p.getFollowingCount(),
                p.isVerified(), p.isBusiness(), p.isRequiresOnboarding(), p.getReputationScore(),
                p.getCity().getId(),
                p.getDiscoveryRadiusKm(),
                p.getAppLanguage(),
                badges
        );
    }

    /** @param badges the profile owner's unlocked badges — see {@link #toDto}. */
    PublicProfileDto toPublicDto(ProfileEntity p, List<UserBadgeDto> badges) {
        return new PublicProfileDto(
                p.getId(), p.getName(), p.getUsername(),
                resolveAvatarUrl(p.getAvatarUrl()), p.getBio(),
                p.getExternalLink(), p.getFollowersCount(), p.getFollowingCount(),
                p.isVerified(), p.isBusiness(), p.getReputationScore(),
                badges
        );
    }

    ProfileSearchResultDto toSearchResultDto(ProfileEntity p) {
        return new ProfileSearchResultDto(p.getId(), p.getName(), p.getUsername(), resolveAvatarUrl(p.getAvatarUrl()));
    }
}
