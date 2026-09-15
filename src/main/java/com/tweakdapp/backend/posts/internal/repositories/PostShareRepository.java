package com.tweakdapp.backend.posts.internal.repositories;

import com.tweakdapp.backend.posts.internal.entities.PostShareEntity;
import com.tweakdapp.backend.posts.internal.entities.PostShareId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostShareRepository extends JpaRepository<PostShareEntity, PostShareId> {

    /**
     * Idempotent repost: inserts the (post, user) row, or does nothing if it already exists.
     * {@code ON CONFLICT DO NOTHING} makes concurrent double-taps race-safe — an exists-then-insert
     * lets both requests pass the check and the loser fail on the composite PK.
     *
     * @return {@code 1} for a new repost, {@code 0} when the user was already reposting it
     */
    @Modifying
    @Query(value = """
            insert into public.post_shares (post_id, user_id)
            values (:postId, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIgnoringConflict(@Param("postId") UUID postId, @Param("userId") UUID userId);

    void deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);

    /**
     * Records that the post's author has been notified about this reposter. The ledger row outlives
     * the repost (an undo keeps it), so this answers "notify?" once per (post, reposter), ever.
     *
     * @return {@code 1} the first time — notify; {@code 0} on every later repost, including after an undo
     */
    @Modifying
    @Query(value = """
            insert into public.post_repost_notifications (post_id, user_id)
            values (:postId, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    int markRepostNotified(@Param("postId") UUID postId, @Param("userId") UUID userId);

    /** Of the given posts, the IDs the viewer has reposted — one query for the whole page. */
    @Query("""
            select ps.id.postId
              from PostShareEntity ps
             where ps.id.userId = :viewerId
               and ps.id.postId in :postIds
            """)
    List<UUID> findRepostedPostIds(@Param("viewerId") UUID viewerId,
                                   @Param("postIds") Collection<UUID> postIds);

    /**
     * Who reposted the given posts among {@code followeeIds}, most recent repost first — the
     * "reposted by" line on a feed page. {@code followeeIds} is a Postgres array literal
     * ({@code {uuid,uuid}}), one bind parameter however many accounts the viewer follows.
     */
    @Query(value = """
            select ps.post_id as "postId", ps.user_id as "userId"
              from public.post_shares ps
             where ps.post_id in (:postIds)
               and ps.user_id = any(cast(:followeeIds as uuid[]))
             order by ps.created_at desc, ps.user_id desc
            """, nativeQuery = true)
    List<RepostRow> findFollowedReposts(@Param("postIds") Collection<UUID> postIds,
                                        @Param("followeeIds") String followeeIds);

    /**
     * One keyset page of a user's reposts, ordered by when they reposted (newest repost first) with the post {@code id} as a total-order tiebreaker. The cursor is the
     * {@code (createdAt, postId)} of the last row of the previous page, or {@code null} for the
     * first page. Pass a {@link Pageable} of {@code size + 1} to detect a further page.
     */
    @Query("""
            select ps
              from PostShareEntity ps
             where ps.id.userId = :userId
               and not exists (select 1 from PostEntity p
                                where p.id = ps.id.postId and p.userId in :hiddenIds)
               and (:firstPage = true
                    or ps.createdAt < :cursorTs
                    or (ps.createdAt = :cursorTs and ps.id.postId < :cursorPostId))
             order by ps.createdAt desc, ps.id.postId desc
            """)
    List<PostShareEntity> findSharedPage(@Param("userId") UUID userId,
                                         @Param("firstPage") boolean firstPage,
                                         @Param("cursorTs") Instant cursorTs,
                                         @Param("cursorPostId") UUID cursorPostId,
                                         @Param("hiddenIds") Collection<UUID> hiddenIds,
                                         Pageable pageable);
}
