package com.tweakdapp.backend.profile.internal.repository;

import com.tweakdapp.backend.profile.internal.entity.ProfileEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {
    boolean existsByUsername(String username);
    Optional<ProfileEntity> findByUsername(String username);
    /** Prefix search that skips {@code excludedIds} (accounts a block hides from the searcher). */
    List<ProfileEntity> findTop20ByUsernameStartingWithIgnoreCaseAndIdNotInOrderByUsernameAsc(
            String prefix, Collection<UUID> excludedIds);
    List<ProfileEntity> findAllByIdIn(Collection<UUID> ids);

    /** Which of the given profiles are business accounts — for support-ticket badges etc. */
    @Query("select p.id from ProfileEntity p where p.id in :ids and p.isBusiness = true")
    List<UUID> findBusinessIds(@Param("ids") Collection<UUID> ids);

    /**
     * The profile row under a pessimistic write lock, for the read-modify-write of
     * {@code reputation_score}. Concurrent awards to the same user serialise behind it instead of
     * racing and losing one of the increments.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProfileEntity p where p.id = :id")
    Optional<ProfileEntity> findByIdForUpdate(@Param("id") UUID id);

    /** The reputation column alone — the profile block on a profile screen, no entity hydration. */
    @Query("select p.reputationScore from ProfileEntity p where p.id = :id")
    Optional<Integer> findReputationScore(@Param("id") UUID id);

    /** The ban columns only — the interceptor's per-request check, no entity hydration needed. */
    @Query("select p.isBanned as banned, p.bannedUntil as bannedUntil from ProfileEntity p where p.id = :id")
    Optional<BanState> findBanState(@Param("id") UUID id);

    interface BanState {
        boolean getBanned();
        Instant getBannedUntil();
    }
}
