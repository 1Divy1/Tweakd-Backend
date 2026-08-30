package com.tweakdapp.backend.feedback.internal.repositories;

import com.tweakdapp.backend.feedback.internal.entities.FeedbackEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<FeedbackEntity, UUID> {

    /** Every feedback the given user submitted, newest first — for their "my feedback" view. */
    List<FeedbackEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // ------------------------------------------------------------------
    // Public board. All queries take optional type/status filters and load
    // size + 1 rows; the service trims the sentinel and encodes the cursor.
    // ------------------------------------------------------------------

    /** First page, "new" sort: newest first. */
    @Query("""
            select f from FeedbackEntity f
            where (:type is null or f.type = :type)
              and (:status is null or f.status = :status)
            order by f.createdAt desc, f.id desc
            """)
    List<FeedbackEntity> findBoardByNew(@Param("type") String type,
                                        @Param("status") String status,
                                        Pageable pageable);

    /** Keyset continuation of {@link #findBoardByNew}. */
    @Query("""
            select f from FeedbackEntity f
            where (:type is null or f.type = :type)
              and (:status is null or f.status = :status)
              and (f.createdAt < :createdAt or (f.createdAt = :createdAt and f.id < :id))
            order by f.createdAt desc, f.id desc
            """)
    List<FeedbackEntity> findBoardByNewAfter(@Param("type") String type,
                                             @Param("status") String status,
                                             @Param("createdAt") Instant createdAt,
                                             @Param("id") UUID id,
                                             Pageable pageable);

    /** First page, "top" sort: most upvoted first. */
    @Query("""
            select f from FeedbackEntity f
            where (:type is null or f.type = :type)
              and (:status is null or f.status = :status)
            order by f.voteCount desc, f.id desc
            """)
    List<FeedbackEntity> findBoardByTop(@Param("type") String type,
                                        @Param("status") String status,
                                        Pageable pageable);

    /**
     * Keyset continuation of {@link #findBoardByTop}. vote_count is mutable, so like the ranked
     * feeds elsewhere in the app this is only eventually consistent across pages.
     */
    @Query("""
            select f from FeedbackEntity f
            where (:type is null or f.type = :type)
              and (:status is null or f.status = :status)
              and (f.voteCount < :voteCount or (f.voteCount = :voteCount and f.id < :id))
            order by f.voteCount desc, f.id desc
            """)
    List<FeedbackEntity> findBoardByTopAfter(@Param("type") String type,
                                             @Param("status") String status,
                                             @Param("voteCount") long voteCount,
                                             @Param("id") UUID id,
                                             Pageable pageable);

    /**
     * "Trending" sort: ranked by votes received in the last 7 days. Returns (feedback_id,
     * recent_votes) pairs in page order; the service loads the entities and re-orders. The recent
     * count is recomputed per request, so pagination is eventually consistent like the top sort.
     */
    @Query(value = """
            select f.id as id, coalesce(v.recent, 0) as recent
            from feedback f
            left join (select feedback_id, count(*) as recent
                       from feedback_votes
                       where created_at > now() - interval '7 days'
                       group by feedback_id) v on v.feedback_id = f.id
            where (cast(:type as text) is null or f.type = :type)
              and (cast(:status as text) is null or f.status = :status)
            order by recent desc, f.id desc
            limit :limit
            """, nativeQuery = true)
    List<TrendingRow> findBoardByTrending(@Param("type") String type,
                                          @Param("status") String status,
                                          @Param("limit") int limit);

    /** Keyset continuation of {@link #findBoardByTrending}. */
    @Query(value = """
            select f.id as id, coalesce(v.recent, 0) as recent
            from feedback f
            left join (select feedback_id, count(*) as recent
                       from feedback_votes
                       where created_at > now() - interval '7 days'
                       group by feedback_id) v on v.feedback_id = f.id
            where (cast(:type as text) is null or f.type = :type)
              and (cast(:status as text) is null or f.status = :status)
              and (coalesce(v.recent, 0), f.id) < (:recent, :id)
            order by recent desc, f.id desc
            limit :limit
            """, nativeQuery = true)
    List<TrendingRow> findBoardByTrendingAfter(@Param("type") String type,
                                               @Param("status") String status,
                                               @Param("recent") long recent,
                                               @Param("id") UUID id,
                                               @Param("limit") int limit);

    /** Interface projection for the trending queries (aliases {@code id} / {@code recent}). */
    interface TrendingRow {
        UUID getId();
        long getRecent();
    }

    // ------------------------------------------------------------------
    // Admin dashboard aggregates
    // ------------------------------------------------------------------

    long countByCreatedAtAfter(Instant since);

    @Query("select f.type as key, count(f) as cnt from FeedbackEntity f group by f.type")
    List<KeyCount> countByType();

    @Query("select f.status as key, count(f) as cnt from FeedbackEntity f group by f.status")
    List<KeyCount> countByStatus();

    interface KeyCount {
        String getKey();
        long getCnt();
    }
}
