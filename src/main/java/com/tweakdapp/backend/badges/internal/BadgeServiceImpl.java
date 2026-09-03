package com.tweakdapp.backend.badges.internal;

import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.dto.BadgeDto;
import com.tweakdapp.backend.badges.dto.BadgeGrantDto;
import com.tweakdapp.backend.badges.dto.BadgeUpsertRequest;
import com.tweakdapp.backend.badges.dto.UserBadgeDto;
import com.tweakdapp.backend.badges.exception.BadgeAlreadyExistsException;
import com.tweakdapp.backend.badges.exception.BadgeInUseException;
import com.tweakdapp.backend.badges.exception.BadgeNotFoundException;
import com.tweakdapp.backend.badges.internal.entity.BadgeEntity;
import com.tweakdapp.backend.badges.internal.entity.UserBadgeEntity;
import com.tweakdapp.backend.badges.internal.repository.BadgeRepository;
import com.tweakdapp.backend.badges.internal.repository.UserBadgeRepository;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
class BadgeServiceImpl implements BadgeService {

    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final StorageService storageService;

    @PersistenceContext
    private EntityManager entityManager;

    BadgeServiceImpl(BadgeRepository badgeRepository,
                     UserBadgeRepository userBadgeRepository,
                     StorageService storageService) {
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.storageService = storageService;
    }

    // ---- awarding -----------------------------------------------------------

    @Override
    @Transactional
    public UserBadgeDto award(UUID userId, String badgeId) {
        return doAward(userId, badgeId, null);
    }

    @Override
    @Transactional
    public UserBadgeDto grant(UUID userId, String badgeId, UUID grantedBy) {
        return doAward(userId, badgeId, grantedBy);
    }

    /**
     * The unlock itself.
     *
     * <p>An unlock already on file short-circuits and is returned as it stands — that is what makes
     * a retried listener harmless, and it is why {@code earnedAt} never moves: the date on the badge
     * is when the user first earned it, not when something last tried to award it.
     *
     * <p>The pre-check is a read followed by a write and so loses a race on its own. The unique
     * index on {@code (user_id, badge_id)} is the actual guarantee: a concurrent insert of the same
     * pair fails at the flush below, the caller's transaction rolls back with the achievement it
     * was awarding, and the retry finds the winning row here.
     *
     * <p>The user is not checked to exist. This module deliberately reads nothing from
     * {@code profiles} — that is what lets {@code profile} depend on it, so a profile response can
     * carry its owner's badges without the two modules forming a cycle. The foreign key still
     * refuses an unlock for a user who is not there; callers award from inside the transaction
     * that just handled that user, so there is nothing to look up.
     */
    private UserBadgeDto doAward(UUID userId, String badgeId, UUID grantedBy) {
        BadgeEntity badge = requireAwardableBadge(badgeId);

        Optional<UserBadgeEntity> existing = userBadgeRepository.findForUserAndBadge(userId, badgeId);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        UserBadgeEntity unlock = new UserBadgeEntity();
        unlock.setId(UUID.randomUUID());
        unlock.setUserId(userId);
        unlock.setBadge(badge);
        unlock.setGrantedBy(grantedBy);

        // id is assigned here, so save() merges rather than persists; created_at is DB-managed
        // (DEFAULT now()), hence the flush-then-refresh to read back what the database stamped.
        UserBadgeEntity saved = userBadgeRepository.saveAndFlush(unlock);
        entityManager.refresh(saved);

        return toDto(saved);
    }

    @Override
    @Transactional
    public boolean revoke(UUID userId, String badgeId) {
        // Deliberately not requireAwardableBadge: a retired badge granted by mistake still has to
        // be removable, and the row is what matters here, not the catalogue entry behind it.
        Optional<UserBadgeEntity> held = userBadgeRepository.findForUserAndBadge(userId, badgeId);
        if (held.isEmpty()) {
            // Never awarded, or already taken back. Both are the state the caller asked for.
            return false;
        }
        userBadgeRepository.delete(held.get());
        return true;
    }

    // ---- reads --------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean hasBadge(UUID userId, String badgeId) {
        return userBadgeRepository.existsByUserIdAndBadgeId(userId, badgeId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserBadgeDto> listUserBadges(UUID userId) {
        return userBadgeRepository.findAllForUser(userId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BadgeDto> listLockedBadges(UUID userId) {
        return badgeRepository.findLockedForUser(userId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BadgeDto> listCatalogue() {
        return badgeRepository.findByAvailableTrueOrderByCreatedAtAsc().stream().map(this::toDto).toList();
    }

    // ---- administration -----------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<BadgeDto> listAllForAdmin() {
        return badgeRepository.findAllByOrderByCreatedAtAsc().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public BadgeDto create(String badgeId, BadgeUpsertRequest request) {
        String id = normaliseId(badgeId);
        if (badgeRepository.existsById(id)) {
            // Not an overwrite: the code is referenced by every unlock, so silently replacing the
            // definition behind it would rewrite what those users think they hold.
            throw new BadgeAlreadyExistsException(id);
        }

        BadgeEntity badge = new BadgeEntity();
        badge.setId(id);
        apply(badge, request);

        BadgeEntity saved = badgeRepository.saveAndFlush(badge);
        entityManager.refresh(saved);   // created_at is DB-managed
        return toDto(saved);
    }

    @Override
    @Transactional
    public BadgeDto update(String badgeId, BadgeUpsertRequest request) {
        BadgeEntity badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> BadgeNotFoundException.byId(badgeId));
        apply(badge, request);
        return toDto(badge);
    }

    @Override
    @Transactional
    public void delete(String badgeId) {
        BadgeEntity badge = badgeRepository.findById(badgeId)
                .orElseThrow(() -> BadgeNotFoundException.byId(badgeId));

        // The FK is ON DELETE RESTRICT, so the database would refuse this anyway — but a constraint
        // violation surfacing as a 500 tells the staff member nothing about what to do instead.
        long holders = userBadgeRepository.countByBadgeId(badgeId);
        if (holders > 0) {
            throw new BadgeInUseException(badgeId, holders);
        }
        badgeRepository.delete(badge);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BadgeGrantDto> listGrantsForAdmin(UUID userId) {
        return userBadgeRepository.findAllForUser(userId).stream()
                .map(unlock -> new BadgeGrantDto(
                        toDto(unlock.getBadge()), unlock.getCreatedAt(), unlock.getGrantedBy()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> holderCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        // Every badge starts at zero so the dashboard renders a row per badge; the grouped query
        // only returns the ones somebody actually holds.
        badgeRepository.findAllByOrderByCreatedAtAsc().forEach(b -> counts.put(b.getId(), 0L));
        for (Object[] row : badgeRepository.countHoldersByBadge()) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    // ---- helpers ------------------------------------------------------------

    /** Resolves a badge that may still be awarded; unknown and retired codes are both refused. */
    private BadgeEntity requireAwardableBadge(String badgeId) {
        if (badgeId == null || badgeId.isBlank()) {
            throw BadgeNotFoundException.byId(String.valueOf(badgeId));
        }
        return badgeRepository.findByIdAndAvailableTrue(badgeId)
                .orElseThrow(() -> badgeRepository.existsById(badgeId)
                        ? BadgeNotFoundException.retired(badgeId)
                        : BadgeNotFoundException.byId(badgeId));
    }

    /**
     * The badge code, canonicalised. Codes are typed into a dashboard form and then referenced from
     * Java constants and every unlock row, so a stray capital or space would be a badge nobody can
     * award.
     */
    private String normaliseId(String badgeId) {
        if (badgeId == null || badgeId.isBlank()) {
            throw BadgeNotFoundException.byId(String.valueOf(badgeId));
        }
        return badgeId.strip().toLowerCase();
    }

    private void apply(BadgeEntity badge, BadgeUpsertRequest request) {
        badge.setTitle(request.title().strip());
        badge.setDescription(blankToNull(request.description()));
        badge.setUnlockedKey(request.unlockedKey().strip());
        badge.setLockedKey(blankToNull(request.lockedKey()));
        badge.setAvailable(request.available());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Resolves the stored R2 keys into full public URLs. The client only ever sees URLs — where the
     * artwork actually lives is this module's business, and the bucket's domain stays a config
     * value rather than something baked into rows.
     */
    private BadgeDto toDto(BadgeEntity badge) {
        return new BadgeDto(
                badge.getId(),
                badge.getTitle(),
                badge.getDescription(),
                storageService.publicUrl(StorageBucket.ASSETS, badge.getUnlockedKey()),
                storageService.publicUrl(StorageBucket.ASSETS, badge.getLockedKey()),
                badge.isAvailable(),
                badge.getCreatedAt());
    }

    private UserBadgeDto toDto(UserBadgeEntity unlock) {
        return new UserBadgeDto(toDto(unlock.getBadge()), unlock.getCreatedAt());
    }
}
