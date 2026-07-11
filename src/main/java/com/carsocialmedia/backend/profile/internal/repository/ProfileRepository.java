package com.carsocialmedia.backend.profile.internal.repository;

import com.carsocialmedia.backend.profile.internal.entity.ProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<ProfileEntity> findTop20ByUsernameStartingWithIgnoreCaseOrderByUsernameAsc(String prefix);
    List<ProfileEntity> findAllByIdIn(Collection<UUID> ids);

    /** Which of the given profiles are business accounts — for support-ticket badges etc. */
    @Query("select p.id from ProfileEntity p where p.id in :ids and p.isBusiness = true")
    List<UUID> findBusinessIds(@Param("ids") Collection<UUID> ids);

    /** The ban columns only — the interceptor's per-request check, no entity hydration needed. */
    @Query("select p.isBanned as banned, p.bannedUntil as bannedUntil from ProfileEntity p where p.id = :id")
    Optional<BanState> findBanState(@Param("id") UUID id);

    interface BanState {
        boolean getBanned();
        Instant getBannedUntil();
    }
}
