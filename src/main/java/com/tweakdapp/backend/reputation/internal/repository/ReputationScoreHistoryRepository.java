package com.tweakdapp.backend.reputation.internal.repository;

import com.tweakdapp.backend.reputation.internal.entity.ReputationScoreHistoryEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads over one user's reputation timeline.
 *
 * <p>Two flavours of almost every read, because revoked entries are visible to the owner but not
 * to anyone else: the {@code ...Live} queries filter {@code revokedAt is null} and back the public
 * timeline, the unfiltered ones back the owner's own. Everything that feeds the score — the count,
 * the per-category split, the last-earned stamp — is live-only in both cases, so the breakdown
 * always adds up to the number on the profile.
 */
public interface ReputationScoreHistoryRepository extends JpaRepository<ReputationScoreHistoryEntity, UUID> {

    // ---- the owner's own timeline (revoked entries included) ----------------

    /**
     * The first page of one user's timeline, newest first. {@code join fetch} on the reason because
     * every entry renders its label — without it this is N+1 across the page.
     */
    @Query("""
            select h from ReputationScoreHistoryEntity h
            join fetch h.reason
            where h.userId = :userId
            order by h.createdAt desc, h.id desc
            """)
    List<ReputationScoreHistoryEntity> findFirstPage(@Param("userId") UUID userId, Pageable pageable);

    /** Keyset continuation of {@link #findFirstPage}: strictly after the cursor row. */
    @Query("""
            select h from ReputationScoreHistoryEntity h
            join fetch h.reason
            where h.userId = :userId
              and (h.createdAt < :createdAt or (h.createdAt = :createdAt and h.id < :id))
            order by h.createdAt desc, h.id desc
            """)
    List<ReputationScoreHistoryEntity> findPageAfter(@Param("userId") UUID userId,
                                                     @Param("createdAt") Instant createdAt,
                                                     @Param("id") UUID id,
                                                     Pageable pageable);

    // ---- the public timeline (revoked entries hidden) ----------------------

    /** {@link #findFirstPage} for anyone other than the owner: revoked entries are not theirs to see. */
    @Query("""
            select h from ReputationScoreHistoryEntity h
            join fetch h.reason
            where h.userId = :userId and h.revokedAt is null
            order by h.createdAt desc, h.id desc
            """)
    List<ReputationScoreHistoryEntity> findFirstPageLive(@Param("userId") UUID userId, Pageable pageable);

    /** Keyset continuation of {@link #findFirstPageLive}. */
    @Query("""
            select h from ReputationScoreHistoryEntity h
            join fetch h.reason
            where h.userId = :userId
              and h.revokedAt is null
              and (h.createdAt < :createdAt or (h.createdAt = :createdAt and h.id < :id))
            order by h.createdAt desc, h.id desc
            """)
    List<ReputationScoreHistoryEntity> findPageAfterLive(@Param("userId") UUID userId,
                                                         @Param("createdAt") Instant createdAt,
                                                         @Param("id") UUID id,
                                                         Pageable pageable);

    // ---- idempotency lookups ------------------------------------------------

    /**
     * The live award of a one-time reason, if the user already has it — the idempotency lookup for
     * reasons with {@code is_repeatable = false}, which can be earned once ever. A revoked entry
     * does not count, so revoking one lets it be earned again.
     */
    @Query("""
            select h from ReputationScoreHistoryEntity h
            join fetch h.reason
            where h.userId = :userId and h.reason.id = :reasonId and h.revokedAt is null
            order by h.createdAt asc, h.id asc
            limit 1
            """)
    Optional<ReputationScoreHistoryEntity> findFirstLiveAward(@Param("userId") UUID userId,
                                                              @Param("reasonId") String reasonId);

    /**
     * The live award of {@code reasonId} for one specific source, if any — the idempotency lookup
     * that makes "attended event Y" pay exactly once. Mirrors the partial unique index on
     * {@code (user_id, reason, source_type, source_id) where revoked_at is null}, which enforces
     * the same thing under a race that this read would lose.
     */
    @Query("""
            select h from ReputationScoreHistoryEntity h
            join fetch h.reason
            where h.userId = :userId
              and h.reason.id = :reasonId
              and h.sourceType = :sourceType
              and h.sourceId = :sourceId
              and h.revokedAt is null
            """)
    Optional<ReputationScoreHistoryEntity> findLiveBySource(@Param("userId") UUID userId,
                                                            @Param("reasonId") String reasonId,
                                                            @Param("sourceType") String sourceType,
                                                            @Param("sourceId") UUID sourceId);

    /**
     * As {@link #findLiveBySource}, but under a pessimistic write lock — the revocation path, where
     * the row's {@code scoreGain} is about to be read and then subtracted from the profile. Without
     * the lock two concurrent revocations of the same award could both read it as live and subtract
     * twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h from ReputationScoreHistoryEntity h
            where h.userId = :userId
              and h.reason.id = :reasonId
              and h.sourceType = :sourceType
              and h.sourceId = :sourceId
              and h.revokedAt is null
            """)
    Optional<ReputationScoreHistoryEntity> findLiveBySourceForUpdate(@Param("userId") UUID userId,
                                                                     @Param("reasonId") String reasonId,
                                                                     @Param("sourceType") String sourceType,
                                                                     @Param("sourceId") UUID sourceId);

    /**
     * Whether the user earned {@code reasonId} at any point after {@code since} — the guard for
     * time-boxed reasons that have no source row to dedupe against, such as the yearly membership
     * anniversary. Revoked entries do not count.
     */
    @Query("""
            select count(h) > 0 from ReputationScoreHistoryEntity h
            where h.userId = :userId
              and h.reason.id = :reasonId
              and h.revokedAt is null
              and h.createdAt > :since
            """)
    boolean existsLiveSince(@Param("userId") UUID userId,
                            @Param("reasonId") String reasonId,
                            @Param("since") Instant since);

    // ---- summary aggregates (always live-only, so they match the score) ----

    /** How many live entries built the user's score. */
    @Query("select count(h) from ReputationScoreHistoryEntity h where h.userId = :userId and h.revokedAt is null")
    long countLive(@Param("userId") UUID userId);

    /**
     * Earned points per category for one user, as {@code [category, sum]} rows — the profile
     * breakdown bar in a single round trip rather than a scan of the whole timeline. Categories the
     * user has nothing in are simply absent.
     */
    @Query("""
            select h.reason.category, sum(h.scoreGain)
              from ReputationScoreHistoryEntity h
             where h.userId = :userId and h.revokedAt is null
             group by h.reason.category
            """)
    List<Object[]> sumPointsByCategory(@Param("userId") UUID userId);

    /** When the user last earned anything that still stands, or {@code null} if nothing does. */
    @Query("select max(h.createdAt) from ReputationScoreHistoryEntity h where h.userId = :userId and h.revokedAt is null")
    Instant findLastEarnedAt(@Param("userId") UUID userId);
}
