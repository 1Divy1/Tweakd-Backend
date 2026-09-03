package com.tweakdapp.backend.reputation.internal;

import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ReputationAdjustmentDto;
import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.reputation.ReputationService;
import com.tweakdapp.backend.reputation.dto.ReputationEntryDto;
import com.tweakdapp.backend.reputation.dto.ReputationHistoryPageDto;
import com.tweakdapp.backend.reputation.dto.ReputationReasonDto;
import com.tweakdapp.backend.reputation.dto.ReputationSource;
import com.tweakdapp.backend.reputation.dto.ReputationSummaryDto;
import com.tweakdapp.backend.reputation.exception.InvalidReputationAwardException;
import com.tweakdapp.backend.reputation.exception.ReputationReasonNotFoundException;
import com.tweakdapp.backend.reputation.internal.entity.ReputationReasonEntity;
import com.tweakdapp.backend.reputation.internal.entity.ReputationScoreHistoryEntity;
import com.tweakdapp.backend.reputation.internal.repository.ReputationReasonRepository;
import com.tweakdapp.backend.reputation.internal.repository.ReputationScoreHistoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
class ReputationServiceImpl implements ReputationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ReputationScoreHistoryRepository historyRepository;
    private final ReputationReasonRepository reasonRepository;
    private final ProfileService profileService;

    @PersistenceContext
    private EntityManager entityManager;

    ReputationServiceImpl(ReputationScoreHistoryRepository historyRepository,
                          ReputationReasonRepository reasonRepository,
                          ProfileService profileService) {
        this.historyRepository = historyRepository;
        this.reasonRepository = reasonRepository;
        this.profileService = profileService;
    }

    // ---- awarding -----------------------------------------------------------

    @Override
    @Transactional
    public ReputationEntryDto award(UUID userId, String reasonId) {
        ReputationReasonEntity reason = requireAwardableReason(reasonId);
        return doAward(userId, reason, reason.getPoints(), null);
    }

    @Override
    @Transactional
    public ReputationEntryDto award(UUID userId, String reasonId, ReputationSource source) {
        ReputationReasonEntity reason = requireAwardableReason(reasonId);
        return doAward(userId, reason, reason.getPoints(), source);
    }

    @Override
    @Transactional
    public ReputationEntryDto award(UUID userId, String reasonId, int points) {
        return award(userId, reasonId, points, null);
    }

    @Override
    @Transactional
    public ReputationEntryDto award(UUID userId, String reasonId, int points, ReputationSource source) {
        ReputationReasonEntity reason = requireAwardableReason(reasonId);
        if (points == 0) {
            throw InvalidReputationAwardException.zeroPoints(reasonId);
        }
        return doAward(userId, reason, points, source);
    }

    /**
     * The award itself. Order matters: the profile row is moved <em>first</em>, under a pessimistic
     * write lock held by the profile module, and only then is the history row written from the
     * before/after that call reported. Reading the score separately and hoping it had not changed
     * would let two concurrent awards record the same {@code previous_score}.
     *
     * <p>Before any of that, an award already on file short-circuits and is returned as it stands —
     * see {@link #findExistingAward}. The score is not touched in that case, which is the whole
     * point: a retried check-in must not pay twice.
     */
    private ReputationEntryDto doAward(UUID userId,
                                       ReputationReasonEntity reason,
                                       int points,
                                       ReputationSource source) {
        Optional<ReputationScoreHistoryEntity> existing = findExistingAward(userId, reason, source);
        if (existing.isPresent()) {
            return toDto(existing.get());
        }

        // Throws ProfileNotFoundException if there is no such profile — an award to a deleted user
        // should fail loudly rather than leave an orphan history row the FK would reject anyway.
        ReputationAdjustmentDto adjustment = profileService.applyReputationDelta(userId, points);

        ReputationScoreHistoryEntity entry = new ReputationScoreHistoryEntity();
        entry.setId(UUID.randomUUID());
        entry.setUserId(userId);
        entry.setReason(reason);
        // The applied delta, not the requested one: a penalty that hit the zero floor must show
        // what actually happened, or previous + gain would not equal new on the client's timeline.
        entry.setScoreGain(adjustment.appliedDelta());
        entry.setPreviousScore(adjustment.previousScore());
        entry.setNewScore(adjustment.newScore());
        if (source != null) {
            entry.setSourceType(source.type());
            entry.setSourceId(source.id());
            // Snapshotted deliberately: the entry has to stay readable after the event is renamed
            // or deleted, and this module must not have to read another module's tables to render
            // a timeline. See ReputationSource.
            entry.setSourceLabel(source.label());
        }

        // id is set client-side, so save() merges rather than persists and returns a new managed
        // instance; created_at is DB-managed (DEFAULT now()), hence the flush-then-refresh.
        //
        // The flush is also where the partial unique index on
        // (user_id, reason, source_type, source_id) speaks: if a concurrent transaction inserted
        // the same sourced award between findExistingAward above and here, this fails rather than
        // paying twice. The exception propagates — the caller's transaction rolls back with the
        // achievement it was awarding, and the retry finds the winning row and short-circuits.
        ReputationScoreHistoryEntity saved = historyRepository.saveAndFlush(entry);
        entityManager.refresh(saved);

        return toDto(saved);
    }

    /**
     * The award already on file for this (user, reason, source), if any.
     *
     * <p>Two different questions, depending on the reason. A one-time reason
     * ({@code is_repeatable = false}) can be earned once ever, so any prior entry counts. A
     * repeatable one can be earned again and again — just not twice for the <em>same</em> source,
     * so only an entry with the same source counts, and a sourceless award of a repeatable reason
     * is never a duplicate of anything.
     */
    private Optional<ReputationScoreHistoryEntity> findExistingAward(UUID userId,
                                                                     ReputationReasonEntity reason,
                                                                     ReputationSource source) {
        if (!reason.isRepeatable()) {
            return historyRepository.findFirstLiveAward(userId, reason.getId());
        }
        if (source == null) {
            return Optional.empty();
        }
        return historyRepository.findLiveBySource(userId, reason.getId(), source.type(), source.id());
    }

    // ---- revoking -----------------------------------------------------------

    @Override
    @Transactional
    public boolean revoke(UUID userId, String reasonId, ReputationSource source, String reason) {
        if (source == null) {
            throw InvalidReputationAwardException.revocationNeedsSource(reasonId);
        }

        // Under a write lock, because what happens next is a read-modify-write across two tables:
        // this row's applied delta is read, then subtracted from the profile. Two concurrent
        // revocations of the same award would otherwise both see it live and subtract twice.
        Optional<ReputationScoreHistoryEntity> found = historyRepository.findLiveBySourceForUpdate(
                userId, reasonId, source.type(), source.id());
        if (found.isEmpty()) {
            // Never awarded, or already revoked. Both are the state the caller asked for, so this
            // is success — an event-cancelled handler must not blow up because the event never
            // paid out in the first place.
            return false;
        }

        ReputationScoreHistoryEntity entry = found.get();

        // The delta this entry actually applied, negated — symmetric even for a penalty that was
        // clamped at the zero floor, because scoreGain holds the clamped value, not the requested
        // one. Clamping applies again on the way back, so the score cannot go negative here either.
        profileService.applyReputationDelta(userId, -entry.getScoreGain());

        entry.setRevokedAt(Instant.now());
        entry.setRevokedReason(reason == null || reason.isBlank() ? null : reason.strip());
        return true;
    }

    /** Resolves a reason that may still be awarded; retired or unknown codes are refused. */
    private ReputationReasonEntity requireAwardableReason(String reasonId) {
        if (reasonId == null || reasonId.isBlank()) {
            throw ReputationReasonNotFoundException.byId(String.valueOf(reasonId));
        }
        return reasonRepository.findByIdAndActiveTrue(reasonId)
                .orElseThrow(() -> ReputationReasonNotFoundException.byId(reasonId));
    }

    // ---- reads --------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean hasEarnedSince(UUID userId, String reasonId, Instant since) {
        return historyRepository.existsLiveSince(userId, reasonId, since);
    }

    @Override
    @Transactional(readOnly = true)
    public ReputationSummaryDto getSummary(UUID userId) {
        // The total lives on profiles.reputation_score, owned by the profile module — read it from
        // there rather than re-summing the history, so the headline number and the column can never
        // disagree. An empty result is a missing profile, not a zero score.
        int score = profileService.findReputationScore(userId)
                .orElseThrow(() -> ProfileNotFoundException.byUserId(userId.toString()));

        Map<String, Integer> byCategory = new LinkedHashMap<>();
        for (Object[] row : historyRepository.sumPointsByCategory(userId)) {
            byCategory.put((String) row[0], ((Number) row[1]).intValue());
        }

        return new ReputationSummaryDto(
                userId,
                score,
                historyRepository.countLive(userId),
                byCategory,
                historyRepository.findLastEarnedAt(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public ReputationSummaryDto getSummaryByUsername(String username) {
        return getSummary(resolveUsername(username));
    }

    @Override
    @Transactional(readOnly = true)
    public ReputationHistoryPageDto getHistory(UUID userId, String cursor, int size) {
        return readHistory(userId, cursor, size, true);
    }

    @Override
    @Transactional(readOnly = true)
    public ReputationHistoryPageDto getHistoryByUsername(String username, String cursor, int size) {
        return readHistory(resolveUsername(username), cursor, size, false);
    }

    /**
     * The paged read behind both history methods.
     *
     * @param includeRevoked {@code true} for the owner's own timeline, which shows revoked entries
     *                       so a dropped score explains itself; {@code false} for the public one,
     *                       which omits them outright rather than flagging them
     */
    private ReputationHistoryPageDto readHistory(UUID userId, String cursor, int size, boolean includeRevoked) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        // One extra row: its presence is how we know there is a next page, without a count query.
        PageRequest limit = PageRequest.of(0, pageSize + 1);

        ReputationCursor decoded = ReputationCursor.decode(cursor);
        List<ReputationScoreHistoryEntity> rows;
        if (includeRevoked) {
            rows = decoded == null
                    ? historyRepository.findFirstPage(userId, limit)
                    : historyRepository.findPageAfter(userId, decoded.createdAt(), decoded.id(), limit);
        } else {
            rows = decoded == null
                    ? historyRepository.findFirstPageLive(userId, limit)
                    : historyRepository.findPageAfterLive(userId, decoded.createdAt(), decoded.id(), limit);
        }

        boolean hasMore = rows.size() > pageSize;
        List<ReputationScoreHistoryEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
                ? new ReputationCursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode()
                : null;

        return new ReputationHistoryPageDto(page.stream().map(this::toDto).toList(), nextCursor);
    }

    // ---- reference data -----------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ReputationReasonDto> listReasons() {
        return reasonRepository.findByActiveTrueOrderByCategoryAscPointsDesc()
                .stream()
                .map(ReputationReasonEntity::toDto)
                .toList();
    }

    // ---- helpers ------------------------------------------------------------

    private UUID resolveUsername(String username) {
        return profileService.findIdByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));
    }

    private ReputationEntryDto toDto(ReputationScoreHistoryEntity h) {
        ReputationReasonEntity reason = h.getReason();
        return new ReputationEntryDto(
                h.getId(),
                reason.getId(),
                reason.getLabel(),
                reason.getCategory(),
                h.getScoreGain(),
                h.getPreviousScore(),
                h.getNewScore(),
                h.getSourceType(),
                h.getSourceId(),
                h.getSourceLabel(),
                h.getRevokedAt(),
                h.getRevokedReason(),
                h.getCreatedAt());
    }
}
