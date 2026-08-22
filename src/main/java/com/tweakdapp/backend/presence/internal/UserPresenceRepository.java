package com.tweakdapp.backend.presence.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface UserPresenceRepository extends JpaRepository<UserPresenceEntity, UUID> {

    /**
     * FK-safe upsert: the {@code select from profiles} guard makes this a silent no-op for
     * authenticated users without a profile row (staff accounts), where a plain insert would raise
     * an FK violation and poison the surrounding transaction.
     */
    @Modifying
    @Query(value = """
            insert into user_presence (user_id, last_seen_at)
            select p.id, :at from profiles p where p.id = :userId
            on conflict (user_id) do update set last_seen_at = excluded.last_seen_at
            """, nativeQuery = true)
    void upsertLastSeen(@Param("userId") UUID userId, @Param("at") Instant at);

    /**
     * Periodic batch touch for users currently online, so a server restart (which wipes in-memory
     * presence) leaves last-seens at most one flush interval stale. Rows are guaranteed to exist —
     * they were upserted when the user came online — so a plain update suffices.
     */
    @Modifying
    @Query(value = "update user_presence set last_seen_at = :at where user_id in (:ids)", nativeQuery = true)
    void touchAll(@Param("ids") Collection<UUID> ids, @Param("at") Instant at);
}
