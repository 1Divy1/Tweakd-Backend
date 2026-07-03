package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumPostLikeEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumPostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ForumPostLikeRepository extends JpaRepository<ForumPostLikeEntity, ForumPostLikeId> {

    /**
     * Idempotent like: inserts the (post, user) row, or does nothing if it already exists.
     * {@code ON CONFLICT DO NOTHING} makes concurrent double-taps race-safe — a plain
     * exists-then-insert can have both requests pass the check and the loser blow up on the
     * composite-PK constraint.
     */
    @Modifying
    @Query(value = """
            insert into forum_post_likes (post_id, user_id)
            values (:postId, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    void insertIgnoringConflict(@Param("postId") UUID postId, @Param("userId") UUID userId);

    void deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);
}
