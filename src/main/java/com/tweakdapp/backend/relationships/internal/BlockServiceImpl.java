package com.tweakdapp.backend.relationships.internal;

import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.relationships.BlockService;
import com.tweakdapp.backend.relationships.dto.BlockedAccountDto;
import com.tweakdapp.backend.relationships.exception.CannotBlockSelfException;
import com.tweakdapp.backend.shared.blocking.UserBlockedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class BlockServiceImpl implements BlockService {

    private final BlockedAccountRepository blockedAccountRepository;
    private final RelationshipRepository relationshipRepository;
    private final ProfileService profileService;
    private final ApplicationEventPublisher eventPublisher;

    BlockServiceImpl(BlockedAccountRepository blockedAccountRepository,
                     RelationshipRepository relationshipRepository,
                     ProfileService profileService,
                     ApplicationEventPublisher eventPublisher) {
        this.blockedAccountRepository = blockedAccountRepository;
        this.relationshipRepository = relationshipRepository;
        this.profileService = profileService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void block(String currentUserId, String targetUsername) {
        UUID blockerId = UUID.fromString(currentUserId);
        UUID blockedId = getUserIdByUsername(targetUsername);
        if (blockerId.equals(blockedId)) {
            throw new CannotBlockSelfException();
        }

        BlockedId id = new BlockedId(blockerId, blockedId);
        if (blockedAccountRepository.existsById(id)) {
            return;
        }

        BlockedAccountEntity entity = new BlockedAccountEntity();
        entity.setId(id);
        blockedAccountRepository.save(entity);

        // Follows go both ways and are not restored on unblock (same as Instagram). The
        // handle_follow_change trigger keeps both profiles' counters right.
        relationshipRepository.deleteFollowsBetween(blockerId, blockedId);

        eventPublisher.publishEvent(new UserBlockedEvent(blockerId, blockedId));
    }

    @Override
    @Transactional
    public void unblock(String currentUserId, String targetUsername) {
        UUID blockerId = UUID.fromString(currentUserId);
        UUID blockedId = getUserIdByUsername(targetUsername);
        blockedAccountRepository.findById(new BlockedId(blockerId, blockedId))
                .ifPresent(blockedAccountRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BlockedAccountDto> listBlocked(String currentUserId) {
        UUID blockerId = UUID.fromString(currentUserId);
        List<BlockedAccountEntity> rows = blockedAccountRepository.findByBlockerNewestFirst(blockerId);
        if (rows.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = rows.stream().map(row -> row.getId().getBlockedId()).toList();
        Map<UUID, ProfileSearchResultDto> profiles = profileService.findByIds(ids).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));

        return rows.stream()
                .map(row -> {
                    ProfileSearchResultDto p = profiles.get(row.getId().getBlockedId());
                    return p == null ? null
                            : new BlockedAccountDto(p.id(), p.name(), p.username(), p.avatarUrl(), row.getCreatedAt());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private UUID getUserIdByUsername(String username) {
        return profileService
                .findIdByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));
    }
}
