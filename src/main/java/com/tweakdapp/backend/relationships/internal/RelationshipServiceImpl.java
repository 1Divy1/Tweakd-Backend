package com.tweakdapp.backend.relationships.internal;

import com.tweakdapp.backend.relationships.RelationshipService;
import com.tweakdapp.backend.relationships.dto.FollowProfileSearchResult;
import com.tweakdapp.backend.relationships.dto.FollowStatus;
import com.tweakdapp.backend.relationships.dto.FollowStatusDto;
import com.tweakdapp.backend.relationships.exception.CannotFollowSelfException;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
class RelationshipServiceImpl implements RelationshipService {

    private static final String STATUS_ACCEPTED = "accepted";

    private final RelationshipRepository relationshipRepository;
    private final ProfileService profileService;

    RelationshipServiceImpl(RelationshipRepository relationshipRepository,
                            ProfileService profileService) {
        this.relationshipRepository = relationshipRepository;
        this.profileService = profileService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findFollowingIds(UUID userId) {
        return relationshipRepository.findAcceptedFollowingIds(userId);
    }

    @Override
    @Transactional
    public FollowStatusDto follow(String currentUserId, String targetUsername) {
        UUID followerId = UUID.fromString(currentUserId);
        // A block in either direction reads as an unknown account — no hint that it exists.
        UUID followingId = getVisibleUserIdByUsername(followerId, targetUsername);

        if (followerId.equals(followingId)) {
            throw new CannotFollowSelfException();
        }

        RelationshipId id = new RelationshipId(followerId, followingId);
        RelationshipEntity existing = relationshipRepository.findById(id).orElse(null);
        if (existing != null) {
            // Idempotent — same row already exists. Return its current status.
            return new FollowStatusDto(toStatus(existing.getStatus()));
        }

        // All accounts are public, so a follow is accepted immediately. (The Supabase
        // set_follow_initial_status trigger also forces 'accepted'; we set it here too so the
        // returned status is correct without a re-read.)
        RelationshipEntity entity = new RelationshipEntity();
        entity.setId(id);
        entity.setStatus(STATUS_ACCEPTED);
        relationshipRepository.save(entity);

        return new FollowStatusDto(FollowStatus.ACCEPTED);
    }

    @Override
    @Transactional
    public void unfollow(String currentUserId, String targetUsername) {
        UUID followerId = UUID.fromString(currentUserId);
        UUID followingId = getUserIdByUsername(targetUsername);

        RelationshipId id = new RelationshipId(followerId, followingId);
        relationshipRepository.findById(id).ifPresent(relationshipRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowStatusDto getFollowStatus(String currentUserId, String targetUsername) {
        UUID followerId = UUID.fromString(currentUserId);
        UUID followingId = getVisibleUserIdByUsername(followerId, targetUsername);

        RelationshipId id = new RelationshipId(followerId, followingId);
        return relationshipRepository
                .findById(id)
                .map(f -> new FollowStatusDto(toStatus(f.getStatus())))
                .orElse(new FollowStatusDto(FollowStatus.NOT_FOLLOWING));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowProfileSearchResult> getFollowers(String currentUserId, String targetUsername) {
        UUID viewerUserId = UUID.fromString(currentUserId);
        UUID targetUserId = getVisibleUserIdByUsername(viewerUserId, targetUsername);

        List<UUID> followerIds = relationshipRepository.findAcceptedFollowerIds(targetUserId);
        return convertToFollowProfileSearchResult(viewerUserId, followerIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowProfileSearchResult> getFollowing(String currentUserId, String targetUsername) {
        UUID viewerUserId = UUID.fromString(currentUserId);
        UUID targetUserId = getVisibleUserIdByUsername(viewerUserId, targetUsername);

        List<UUID> followingIds = relationshipRepository.findAcceptedFollowingIds(targetUserId);
        return convertToFollowProfileSearchResult(viewerUserId, followingIds);
    }

    @Override
    @Transactional
    public void removeFollower(String currentUserId, String followerUsernameToRemove) {
        UUID userId = UUID.fromString(currentUserId);
        UUID followerId = getUserIdByUsername(followerUsernameToRemove);

        RelationshipId id = new RelationshipId(followerId, userId);
        relationshipRepository.findById(id).ifPresent(relationshipRepository::delete);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private UUID getVisibleUserIdByUsername(UUID viewerId, String username) {
        return profileService
                .findVisibleIdByUsername(viewerId, username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));
    }

    private UUID getUserIdByUsername(String username) {
        return profileService
                .findIdByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));
    }

    private List<FollowProfileSearchResult> convertToFollowProfileSearchResult(UUID viewerId, List<UUID> ids) {
        // Accounts a block separates from the viewer are left out of anyone's lists.
        Set<UUID> hidden = profileService.findHiddenProfileIds(viewerId);
        if (!hidden.isEmpty()) {
            ids = ids.stream().filter(id -> !hidden.contains(id)).toList();
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, ProfileSearchResultDto> byId = indexById(profileService.findByIds(ids));
        // Single bulk query: which of these profiles does the viewer already follow?
        Set<UUID> followedByViewer = Set.copyOf(
                relationshipRepository.findAcceptedFollowingIdsIn(viewerId, ids));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(p -> new FollowProfileSearchResult(
                        p.id(),
                        p.username(),
                        p.avatarUrl(),
                        followedByViewer.contains(p.id())))
                .toList();
    }

    private static Map<UUID, ProfileSearchResultDto> indexById(List<ProfileSearchResultDto> dtos) {
        Map<UUID, ProfileSearchResultDto> map = new HashMap<>();
        for (ProfileSearchResultDto dto : dtos) {
            map.put(dto.id(), dto);
        }
        return map;
    }

    private static FollowStatus toStatus(String dbStatus) {
        return STATUS_ACCEPTED.equals(dbStatus) ? FollowStatus.ACCEPTED : FollowStatus.NOT_FOLLOWING;
    }
}
