package com.tweakdapp.backend.posts.internal.repositories;

import com.tweakdapp.backend.posts.internal.entities.PostEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Collection;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {

    List<PostEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * One keyset page of a user's posts, newest first, with the post {@code id} as a total-order
     * tiebreaker. The cursor is the {@code (createdAt, id)} of the last row of the previous page;
     * for the first page pass {@code firstPage = true} (the cursor values are then ignored). Pass a
     * {@link Pageable} of {@code size + 1} to detect a further page.
     *
     * <p>The first-page branch uses a typed {@code :firstPage} flag rather than {@code :cursorTs is
     * null}: a standalone {@code IS NULL} on a bound {@code null} leaves Postgres unable to infer the
     * parameter's type. Here the cursor params only ever appear next to typed columns.
     */
    @Query("""
            select p
              from PostEntity p
             where p.userId = :userId
               and (:firstPage = true
                    or p.createdAt < :cursorTs
                    or (p.createdAt = :cursorTs and p.id < :cursorId))
             order by p.createdAt desc, p.id desc
            """)
    List<PostEntity> findUserPostPage(@Param("userId") UUID userId,
                                      @Param("firstPage") boolean firstPage,
                                      @Param("cursorTs") Instant cursorTs,
                                      @Param("cursorId") UUID cursorId,
                                      Pageable pageable);

    /**
     * One keyset page of the global feed: post ids with the score they rank by, highest first, the
     * post {@code id} as a total-order tiebreaker. The score is {@code ranking_score} (maintained by
     * a Supabase trigger from engagement and age) — except for a post that one of
     * {@code followeeIds} reposted, which ranks as if it had been published at the most recent such
     * repost: its {@code ranking_score} plus the repost's distance from the post's creation, in the
     * same 45000-second units the trigger uses for age. So the post keeps every point of engagement
     * and gains exactly the freshness of the repost, and the weights live only in the trigger.
     *
     * <p>The two populations are paged separately and merged: posts nobody the viewer follows
     * reposted come off {@code idx_posts_ranking_keyset} in index order; followed reposts are
     * bounded by what the viewer's followees reposted. Keeping them disjoint ({@code not exists})
     * is what makes {@code limit} on the first branch safe.
     *
     * <p>The cursor is the {@code (score, id)} of the last row of the previous page; the first page
     * passes {@code +Infinity} so every row qualifies. {@code followeeIds} is a Postgres array
     * literal ({@code {uuid,uuid}}, {@code {}} for none) — one bind parameter however many accounts
     * the viewer follows. {@code hiddenIds} (same literal form) are authors a block separates from the
     * viewer; their posts are left out of both branches. Pass {@code size + 1} as {@code limit} to
     * detect a further page.
     *
     * <p>Note: scores are mutable (engagement moves {@code ranking_score}, a new repost moves the
     * boost), so a post may occasionally repeat or be skipped across pages — an accepted trade-off
     * for a ranked "most viral" feed.
     */
    @Query(value = """
            with reposted as (
                select ps.post_id, max(ps.created_at) as reposted_at
                  from public.post_shares ps
                 where ps.user_id = any(cast(:followeeIds as uuid[]))
                 group by ps.post_id
            ),
            boosted as (
                select p.id,
                       p.ranking_score
                         + greatest(cast(extract(epoch from r.reposted_at) as double precision)
                                    - cast(extract(epoch from p.created_at) as double precision), 0)
                           / 45000.0 as score
                  from reposted r
                  join public.posts p on p.id = r.post_id
                 where p.user_id <> all(cast(:hiddenIds as uuid[]))
            ),
            plain as (
                select p.id, p.ranking_score as score
                  from public.posts p
                 where not exists (select 1 from reposted r where r.post_id = p.id)
                   and p.user_id <> all(cast(:hiddenIds as uuid[]))
                   and (p.ranking_score < :cursorScore
                        or (p.ranking_score = :cursorScore and p.id < :cursorId))
                 order by p.ranking_score desc, p.id desc
                 limit :limit
            )
            select u.id as "id", u.score as "score"
              from (select id, score from plain
                    union all
                    select id, score from boosted) u
             where u.score < :cursorScore
                or (u.score = :cursorScore and u.id < :cursorId)
             order by u.score desc, u.id desc
             limit :limit
            """, nativeQuery = true)
    List<RankedPostRow> findRankedPostPage(@Param("followeeIds") String followeeIds,
                                           @Param("hiddenIds") String hiddenIds,
                                           @Param("cursorScore") double cursorScore,
                                           @Param("cursorId") UUID cursorId,
                                           @Param("limit") int limit);

    /**
     * When this participant card was last shared to the feed, or {@code null} if never (or every
     * such post has since been deleted) — what the repost cooldown checks.
     */
    @Query("""
            select max(p.createdAt)
              from PostEntity p
             where p.participantCardEventId = :eventId
               and p.participantCardCarId = :carId
            """)
    Instant findLastParticipantCardPostAt(@Param("eventId") UUID eventId, @Param("carId") UUID carId);

    /**
     * Serialises concurrent shares of one card until the transaction ends, so a double tap cannot slip
     * two posts past the cooldown check. A transaction-scoped advisory lock: released on commit or
     * rollback, no row touched.
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended(:key, 0))", nativeQuery = true)
    Integer lockParticipantCard(@Param("key") String key);

    /**
     * The post sharing this modification, if it has been shared. A mod gets one post -- the partial
     * unique index on the column is what guarantees that -- so this makes the share idempotent:
     * a second attempt hands back the first post.
     */
    Optional<PostEntity> findByModShareModificationId(UUID modificationId);
}
