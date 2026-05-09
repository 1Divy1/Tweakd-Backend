package com.carsocialmedia.backend.profile.internal;

import com.carsocialmedia.backend.profile.OnboardingRequest;
import com.carsocialmedia.backend.profile.ProfileBecamePublicEvent;
import com.carsocialmedia.backend.profile.ProfileDto;
import com.carsocialmedia.backend.profile.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.PublicProfileDto;
import com.carsocialmedia.backend.profile.exception.ProfileNotFoundException;
import com.carsocialmedia.backend.profile.exception.UsernameAlreadyTakenException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;
    private final ApplicationEventPublisher eventPublisher;

    ProfileServiceImpl(ProfileRepository profileRepository,
                       ApplicationEventPublisher eventPublisher) {
        this.profileRepository = profileRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileDto getProfile(String userId) {
        return profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId))
                .toDto();
    }

    @Override
    @Transactional
    public ProfileDto completeOnboarding(String userId, OnboardingRequest request) {
        ProfileEntity profile = profileRepository
                .findById(UUID.fromString(userId))
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId));

        if (profileRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyTakenException(request.username());
        }

        profile.setUsername(request.username());
        profile.setBio(request.bio());
        profile.setRequiresOnboarding(false);

        try {
            profileRepository.saveAndFlush(profile);
        } catch (DataIntegrityViolationException ex) {
            throw new UsernameAlreadyTakenException(request.username());
        }

        return profile.toDto();
    }

    @Override
    @Transactional
    public ProfileDto setPrivacy(String userId, boolean isPrivate) {
        UUID id = UUID.fromString(userId);
        ProfileEntity profile = profileRepository
                .findById(id)
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId));

        boolean wasPrivate = profile.isPrivate();
        if (wasPrivate == isPrivate) {
            return profile.toDto();
        }

        profile.setPrivate(isPrivate);
        profileRepository.save(profile);

        // private -> public: any pending follow requests should be auto-accepted.
        // The follow module owns the follows table, so we publish a domain
        // event and let it react.
        if (wasPrivate && !isPrivate) {
            eventPublisher.publishEvent(new ProfileBecamePublicEvent(id));
        }

        return profile.toDto();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicProfileDto getPublicProfileByUsername(String username) {
        return profileRepository
                .findByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username))
                .toPublicDto();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileSearchResultDto> searchByUsername(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return profileRepository
                .findTop20ByUsernameStartingWithIgnoreCaseOrderByUsernameAsc(prefix)
                .stream()
                .map(ProfileEntity::toSearchResultDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findIdByUsername(String username) {
        return profileRepository.findByUsername(username).map(ProfileEntity::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPrivate(UUID userId) {
        return profileRepository.findById(userId)
                .map(ProfileEntity::isPrivate)
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfileSearchResultDto> findByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return profileRepository.findAllByIdIn(ids)
                .stream()
                .map(ProfileEntity::toSearchResultDto)
                .toList();
    }
}
