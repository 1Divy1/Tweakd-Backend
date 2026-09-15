package com.tweakdapp.backend.posts.internal.repositories;

import com.tweakdapp.backend.posts.internal.entities.PostLikeEntity;
import com.tweakdapp.backend.posts.internal.entities.PostLikeId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLikeEntity, PostLikeId> {

    /**
     * Idempotent like: inserts the (post, user) row, or does nothing if it already exists.
     * {@code ON CONFLICT DO NOTHING} makes concurrent double-taps race-safe — an exists-then-insert
     * lets both requests pass the check and the loser fail on the composite PK.
     *
     * @return {@code 1} for a new like, {@code 0} when the user already liked the post
     */
    @Modifying
    @Query(value = """
            insert into public.post_likes (post_id, user_id)
            values (:postId, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIgnoringConflict(@Param("postId") UUID postId, @Param("userId") UUID userId);

    void deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);

    /**
     * Records that the post's author has been notified about this liker. The ledger row outlives the
     * like (an unlike keeps it), so this answers "notify?" once per (post, liker), ever.
     *
     * @return {@code 1} the first time — notify; {@code 0} on every later like, including after an unlike
     */
    @Modifying
    @Query(value = """
            insert into public.post_like_notifications (post_id, user_id)
            values (:postId, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    int markLikeNotified(@Param("postId") UUID postId, @Param("userId") UUID userId);

    /** Of the given posts, the IDs the viewer has liked — one query for the whole page. */
    @Query("""
            select pl.id.postId
              from PostLikeEntity pl
             where pl.id.userId = :viewerId
               and pl.id.postId in :postIds
            """)
    List<UUID> findLikedPostIds(@Param("viewerId") UUID viewerId,
                                @Param("postIds") Collection<UUID> postIds);

    /**
     * One keyset page of a post's likes (the likers), ordered most-recent first with the
     * liker's {@code user_id} as a total-order tiebreaker. The cursor is the
     * {@code (createdAt, userId)} of the last row of the previous page, or {@code null} for
     * the first page. Pass a {@link Pageable} of {@code size + 1} to detect a further page.
     */
    @Query("""
            select pl
              from PostLikeEntity pl
             where pl.id.postId = :postId
               and pl.id.userId not in :hiddenIds
               and (:firstPage = true
                    or pl.createdAt < :cursorTs
                    or (pl.createdAt = :cursorTs and pl.id.userId < :cursorUserId))
             order by pl.createdAt desc, pl.id.userId desc
            """)
    List<PostLikeEntity> findLikerPage(@Param("postId") UUID postId,
                                       @Param("firstPage") boolean firstPage,
                                       @Param("cursorTs") Instant cursorTs,
                                       @Param("cursorUserId") UUID cursorUserId,
                                       @Param("hiddenIds") Collection<UUID> hiddenIds,
                                       Pageable pageable);
}
