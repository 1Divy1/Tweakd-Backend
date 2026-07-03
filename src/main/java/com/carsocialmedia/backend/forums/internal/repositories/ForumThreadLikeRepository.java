package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumThreadLikeEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ForumThreadLikeRepository extends JpaRepository<ForumThreadLikeEntity, ForumThreadLikeId> {

    /**
     * Idempotent like: inserts the (thread, user) row, or does nothing if it already exists.
     * {@code ON CONFLICT DO NOTHING} makes concurrent double-taps race-safe — a plain
     * exists-then-insert can have both requests pass the check and the loser blow up on the
     * composite-PK constraint.
     */
    @Modifying
    @Query(value = """
            insert into forum_thread_likes (thread_id, user_id)
            values (:threadId, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    void insertIgnoringConflict(@Param("threadId") UUID threadId, @Param("userId") UUID userId);

    void deleteByIdThreadIdAndIdUserId(UUID threadId, UUID userId);
}
