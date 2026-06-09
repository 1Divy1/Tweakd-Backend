package com.carsocialmedia.backend.follow.internal;

import com.carsocialmedia.backend.follow.FollowService;
import com.carsocialmedia.backend.follow.dto.FollowProfileSearchResult;
import com.carsocialmedia.backend.follow.dto.FollowRequestDto;
import com.carsocialmedia.backend.follow.dto.FollowStatus;
import com.carsocialmedia.backend.follow.dto.FollowStatusDto;
import com.carsocialmedia.backend.follow.exception.CannotFollowSelfException;
import com.carsocialmedia.backend.follow.exception.FollowRequestNotFoundException;
import com.carsocialmedia.backend.follow.exception.PrivateProfileException;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.exception.ProfileNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
class FollowServiceImpl implements FollowService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_ACCEPTED = "accepted";

    private final FollowRepository followRepository;
    private final ProfileService profileService;

    @PersistenceContext
    private EntityManager entityManager;

    FollowServiceImpl(FollowRepository followRepository,
                      ProfileService profileService) {
        this.followRepository = followRepository;
        this.profileService = profileService;
    }

    @Override
    @Transactional
    public FollowStatusDto follow(String currentUserId, String targetUsername) {
        UUID followerId = UUID.fromString(currentUserId);
        UUID followingId = getUserIdByUsername(targetUsername);

        if (followerId.equals(followingId)) {
            throw new CannotFollowSelfException();
        }

        FollowId id = new FollowId(followerId, followingId);
        FollowEntity existing = followRepository.findById(id).orElse(null);
        if (existing != null) {
            // Idempotent — same row already exists. Return its current status.
            return new FollowStatusDto(toStatus(existing.getStatus()));
        }

        FollowEntity entity = new FollowEntity();
        entity.setId(id);
        // status is set by the BEFORE INSERT trigger in Supabase based on the
        // target's is_private flag; we deliberately leave it null here.
        FollowEntity saved = followRepository.saveAndFlush(entity);

        // The INSERT is flushed but the managed entity still holds the null status
        // we wrote — the trigger's value never round-tripped back. refresh() forces
        // a SELECT so the trigger-set value becomes visible. We refresh the entity
        // returned by saveAndFlush (the managed one), not the original reference.
        entityManager.refresh(saved);
        return new FollowStatusDto(toStatus(saved.getStatus()));
    }

    @Override
    @Transactional
    public void unfollow(String currentUserId, String targetUsername) {
        UUID followerId = UUID.fromString(currentUserId);
        UUID followingId = getUserIdByUsername(targetUsername);

        FollowId id = new FollowId(followerId, followingId);
        followRepository.findById(id).ifPresent(followRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowStatusDto getFollowStatus(String currentUserId, String targetUsername) {
        UUID followerId = UUID.fromString(currentUserId);
        UUID followingId = getUserIdByUsername(targetUsername);

        FollowId id = new FollowId(followerId, followingId);
        return followRepository
                .findById(id)
                .map(f -> new FollowStatusDto(toStatus(f.getStatus())))
                .orElse(new FollowStatusDto(FollowStatus.NOT_FOLLOWING));
    }

    // TODO: Logic not implemented yet
    @Override
    @Transactional(readOnly = true)
    public List<FollowRequestDto> getPendingRequests(String currentUserId) {
        UUID userId = UUID.fromString(currentUserId);

        List<UUID> requesterIds = followRepository.findPendingFollowerIds(userId);
        if (requesterIds.isEmpty()) {
            return List.of();
        }

        // Hydrate profile data while preserving the order returned by the query.
        Map<UUID, ProfileSearchResultDto> profilesById = indexById(
                profileService.findByIds(requesterIds));

        // We need the createdAt for each request, so re-fetch the entities by id.
        // The list is small (pending requests for a single user), so this is fine.
        Map<UUID, FollowEntity> rowsByRequester = new HashMap<>();
        for (UUID requesterId : requesterIds) {
            followRepository.findById(new FollowId(requesterId, userId))
                    .ifPresent(row -> rowsByRequester.put(requesterId, row));
        }

        return requesterIds.stream()
                .map(requesterId -> {
                    ProfileSearchResultDto p = profilesById.get(requesterId);
                    FollowEntity row = rowsByRequester.get(requesterId);
                    if (p == null || row == null) {
                        return null;
                    }
                    return new FollowRequestDto(
                            p.id(),
                            p.username(),
                            p.avatarUrl(),
                            row.getCreatedAt()
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // TODO: Logic not implemented yet
    @Override
    @Transactional
    public void acceptRequest(String currentUserId, String requesterUsername) {
        UUID userId = UUID.fromString(currentUserId);
        UUID requesterId = getUserIdByUsername(requesterUsername);

        FollowEntity row = followRepository.findForUpdate(requesterId, userId)
                .orElseThrow(() -> new FollowRequestNotFoundException(requesterUsername));

        if (!STATUS_PENDING.equals(row.getStatus())) {
            // Already accepted (or unknown status) — treat as a no-op for idempotency.
            return;
        }

        row.setStatus(STATUS_ACCEPTED);
        followRepository.save(row);
        // The handle_follow_change AFTER UPDATE trigger increments the counters.
    }

    // TODO: Logic not implemented yet
    @Override
    @Transactional
    public void rejectRequest(String currentUserId, String requesterUsername) {
        UUID userId = UUID.fromString(currentUserId);
        UUID requesterId = getUserIdByUsername(requesterUsername);

        FollowId id = new FollowId(requesterId, userId);
        FollowEntity row = followRepository.findById(id)
                .orElseThrow(() -> new FollowRequestNotFoundException(requesterUsername));

        if (!STATUS_PENDING.equals(row.getStatus())) {
            // Reject only applies to pending requests.
            throw new FollowRequestNotFoundException(requesterUsername);
        }

        followRepository.delete(row);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowProfileSearchResult> getFollowers(String currentUserId, String targetUsername) {
        UUID viewerUserId = UUID.fromString(currentUserId);
        UUID targetUserId = getUserIdByUsername(targetUsername);

        // TODO: For now, all profiles are public
        // ensureCanViewSocialGraph(viewerUserId, targetUserId, targetUsername);

        List<UUID> followerIds = followRepository.findAcceptedFollowerIds(targetUserId);
        return convertToFollowProfileSearchResult(viewerUserId, followerIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FollowProfileSearchResult> getFollowing(String currentUserId, String targetUsername) {
        UUID viewerUserId = UUID.fromString(currentUserId);
        UUID targetUserId = getUserIdByUsername(targetUsername);

        // TODO: For now, all profiles are public
        // ensureCanViewSocialGraph(viewerUserId, targetUserId, targetUsername);

        List<UUID> followingIds = followRepository.findAcceptedFollowingIds(targetUserId);
        return convertToFollowProfileSearchResult(viewerUserId, followingIds);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAcceptedFollower(UUID viewerId, UUID targetId) {
        return followRepository
                .existsByIdFollowerIdAndIdFollowingIdAndStatus(viewerId, targetId, STATUS_ACCEPTED);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private UUID getUserIdByUsername(String username) {
        return profileService
                .findIdByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));
    }

    private void ensureCanViewSocialGraph(UUID viewerId, UUID targetId, String targetUsername) {
        if (viewerId.equals(targetId)) {
            return; // owner can always see their own graph
        }
        if (!profileService.isPrivate(targetId)) {
            return; // public profiles are always visible
        }
        boolean isAcceptedFollower = followRepository
                .existsByIdFollowerIdAndIdFollowingIdAndStatus(viewerId, targetId, STATUS_ACCEPTED);
        if (!isAcceptedFollower) {
            throw new PrivateProfileException(targetUsername);
        }
    }

    private List<FollowProfileSearchResult> convertToFollowProfileSearchResult(UUID viewerId, List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, ProfileSearchResultDto> byId = indexById(profileService.findByIds(ids));
        // Single bulk query: which of these profiles does the viewer already follow?
        Set<UUID> followedByViewer = Set.copyOf(
                followRepository.findAcceptedFollowingIdsIn(viewerId, ids));
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

    private static Map<UUID, ProfileSearchResultDto> indexById (List<ProfileSearchResultDto> dtos) {
        Map<UUID, ProfileSearchResultDto> map = new HashMap<>();
        for (ProfileSearchResultDto dto : dtos) {
            map.put(dto.id(), dto);
        }
        return map;
    }

    private static FollowStatus toStatus(String dbStatus) {
        if (STATUS_ACCEPTED.equals(dbStatus)) {
            return FollowStatus.ACCEPTED;
        }
        if (STATUS_PENDING.equals(dbStatus)) {
            return FollowStatus.PENDING;
        }
        return FollowStatus.NOT_FOLLOWING;
    }

    /**
     * Helper exposed package-privately for the event listener: bulk-accept all pending
     * follow requests for {@code userId}. The Supabase trigger will increment counters
     * per row.
     */
    @Transactional
    void acceptAllPendingFor(UUID userId) {
        followRepository.acceptAllPendingFor(userId);
    }
}
