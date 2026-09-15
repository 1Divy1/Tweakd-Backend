package com.tweakdapp.backend.forums.internal.repositories;

import com.tweakdapp.backend.forums.internal.entities.ForumPostLikeEntity;
import com.tweakdapp.backend.forums.internal.entities.ForumPostLikeId;
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
     *
     * @return the number of rows inserted: {@code 1} for a genuinely new like, {@code 0} when the
     *         like already existed (so the caller can notify only on a real like).
     */
    @Modifying
    @Query(value = """
            insert into forum_post_likes (post_id, user_id)
            values (:postId, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIgnoringConflict(@Param("postId") UUID postId, @Param("userId") UUID userId);

    void deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);

    /**
     * Records that the reply's author has been notified about this liker. The ledger row outlives the
     * like (an unlike keeps it), so this answers "notify?" once per (reply, liker), ever.
     *
     * @return {@code 1} the first time — notify; {@code 0} on every later like, including after an unlike
     */
    @Modifying
    @Query(value = """
            insert into forum_post_like_notifications (post_id, user_id)
            values (:postId, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    int markLikeNotified(@Param("postId") UUID postId, @Param("userId") UUID userId);
}
