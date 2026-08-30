package com.tweakdapp.backend.notification.internal.repositories;

import com.tweakdapp.backend.notification.internal.entities.UserDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDeviceEntity, UUID> {

    /**
     * Idempotent upsert keyed on the token. A token that already exists is <em>reassigned</em> to
     * the calling user — that is the device-handoff case (someone logs out and a second account
     * logs in on the same phone), and it is why the unique constraint is on {@code token} alone.
     *
     * <p>Native because JPA has no upsert. {@code updated_at} is left to the DB trigger.
     */
    @Modifying
    @Query(value = """
            insert into user_devices_firebase_token (id, user_id, token, platform, app_version, locale)
            values (:id, :userId, :token, :platform, :appVersion, :locale)
            on conflict (token) do update
               set user_id     = excluded.user_id,
                   platform    = excluded.platform,
                   app_version = excluded.app_version,
                   locale      = excluded.locale
            """, nativeQuery = true)
    void upsert(@Param("id") UUID id,
                @Param("userId") UUID userId,
                @Param("token") String token,
                @Param("platform") String platform,
                @Param("appVersion") String appVersion,
                @Param("locale") String locale);

    /**
     * Unregister, scoped to the owner. The {@code userId} predicate is load-bearing security: without
     * it any authenticated user could unregister another user's device by guessing or replaying a
     * token, silently cutting off their push notifications.
     */
    @Modifying
    @Query("delete from UserDeviceEntity d where d.token = :token and d.userId = :userId")
    int deleteByTokenAndUserId(@Param("token") String token, @Param("userId") UUID userId);

    /** Every device belonging to any of these recipients — the push fan-out lookup. */
    List<UserDeviceEntity> findByUserIdIn(Collection<UUID> userIds);

    /**
     * Ids of the caller's devices beyond the newest {@code keep}, oldest first. Used to enforce the
     * per-user device cap so a valid JWT cannot be used to grow this table without bound.
     */
    @Query(value = """
            select id from user_devices_firebase_token
             where user_id = :userId
             order by updated_at desc
            offset :keep
            """, nativeQuery = true)
    List<UUID> findIdsBeyondCap(@Param("userId") UUID userId, @Param("keep") int keep);

    /** Bulk prune of tokens FCM told us are dead. No-op on an empty collection. */
    @Modifying
    @Query("delete from UserDeviceEntity d where d.token in :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);
}
