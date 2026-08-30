package com.tweakdapp.backend.feedbackfeed.internal.repositories;

import com.tweakdapp.backend.feedbackfeed.internal.entities.FeedbackFeedMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads over the feed.
 *
 * <p>The paged queries are <strong>native</strong> so they can use Postgres row-value comparisons
 * ({@code (created_at, id) < (?, ?)}), which map straight onto the composite partial indexes the
 * table carries; JPQL has no row-value syntax and would force a hand-expanded OR that the planner
 * uses less well.
 *
 * <p>Two conventions in every paged query:
 * <ul>
 *   <li>a {@code :firstPage} boolean rather than a null check on the cursor, and
 *   <li>explicit {@code cast(... as ...)} on every cursor parameter — Hibernate renders each
 *       parameter occurrence as its own untyped JDBC placeholder, so a null cursor otherwise
 *       reaches Postgres with no type context and fails with "could not determine data type of
 *       parameter".
 * </ul>
 */
public interface FeedbackFeedMessageRepository extends JpaRepository<FeedbackFeedMessageEntity, UUID> {

    /** A single visible message — staff-removed rows read as absent. */
    Optional<FeedbackFeedMessageEntity> findByIdAndDeletedFalse(UUID id);

    /**
     * Main feed, newest first. Matches {@code idx_feedback_feed_messages_active_created}.
     */
    @Query(value = """
            select m.* from feedback_feed_messages m
            where m.is_deleted = false
              and m.status <> 'completed'
              and (:firstPage = true
                   or (m.created_at, m.id) < (cast(:cursorAt as timestamptz), cast(:cursorId as uuid)))
            order by m.created_at desc, m.id desc
            limit :size
            """, nativeQuery = true)
    List<FeedbackFeedMessageEntity> findNewest(@Param("firstPage") boolean firstPage,
                                               @Param("cursorAt") Instant cursorAt,
                                               @Param("cursorId") UUID cursorId,
                                               @Param("size") int size);

    /**
     * Main feed, oldest first — the same partial index, scanned backwards.
     */
    @Query(value = """
            select m.* from feedback_feed_messages m
            where m.is_deleted = false
              and m.status <> 'completed'
              and (:firstPage = true
                   or (m.created_at, m.id) > (cast(:cursorAt as timestamptz), cast(:cursorId as uuid)))
            order by m.created_at asc, m.id asc
            limit :size
            """, nativeQuery = true)
    List<FeedbackFeedMessageEntity> findOldest(@Param("firstPage") boolean firstPage,
                                               @Param("cursorAt") Instant cursorAt,
                                               @Param("cursorId") UUID cursorId,
                                               @Param("size") int size);

    /**
     * Main feed by net votes, highest first. Matches {@code idx_feedback_feed_messages_popular}.
     * Also serves the dashboard's "top voted" panel, which is just its first page.
     */
    @Query(value = """
            select m.* from feedback_feed_messages m
            where m.is_deleted = false
              and m.status <> 'completed'
              and (:firstPage = true
                   or (m.net_votes, m.id) < (cast(:cursorScore as int), cast(:cursorId as uuid)))
            order by m.net_votes desc, m.id desc
            limit :size
            """, nativeQuery = true)
    List<FeedbackFeedMessageEntity> findMostVoted(@Param("firstPage") boolean firstPage,
                                                  @Param("cursorScore") Integer cursorScore,
                                                  @Param("cursorId") UUID cursorId,
                                                  @Param("size") int size);

    /**
     * The "Completed requests" section, most recently shipped first. Matches
     * {@code idx_feedback_feed_messages_completed_shipped}.
     *
     * <p>{@code completed_at} is guaranteed non-null on these rows — the stamping trigger sets it on
     * every transition into {@code completed} — so the keyset needs no null handling.
     */
    @Query(value = """
            select m.* from feedback_feed_messages m
            where m.is_deleted = false
              and m.status = 'completed'
              and (:firstPage = true
                   or (m.completed_at, m.id) < (cast(:cursorAt as timestamptz), cast(:cursorId as uuid)))
            order by m.completed_at desc, m.id desc
            limit :size
            """, nativeQuery = true)
    List<FeedbackFeedMessageEntity> findCompleted(@Param("firstPage") boolean firstPage,
                                                  @Param("cursorAt") Instant cursorAt,
                                                  @Param("cursorId") UUID cursorId,
                                                  @Param("size") int size);

    /**
     * The dashboard's list: every status, optional category/status filters, and staff-removed rows
     * included on request so a moderator can review what was taken down.
     */
    @Query(value = """
            select m.* from feedback_feed_messages m
            where (:includeRemoved = true or m.is_deleted = false)
              and (cast(:type as text) is null or m.type = cast(:type as text))
              and (cast(:status as text) is null or m.status = cast(:status as text))
              and (:firstPage = true
                   or (m.created_at, m.id) < (cast(:cursorAt as timestamptz), cast(:cursorId as uuid)))
            order by m.created_at desc, m.id desc
            limit :size
            """, nativeQuery = true)
    List<FeedbackFeedMessageEntity> findForAdmin(@Param("type") String type,
                                                 @Param("status") String status,
                                                 @Param("includeRemoved") boolean includeRemoved,
                                                 @Param("firstPage") boolean firstPage,
                                                 @Param("cursorAt") Instant cursorAt,
                                                 @Param("cursorId") UUID cursorId,
                                                 @Param("size") int size);
}
