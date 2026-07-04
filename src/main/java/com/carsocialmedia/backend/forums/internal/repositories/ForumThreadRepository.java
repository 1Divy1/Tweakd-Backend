package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumThreadEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumThreadRepository extends JpaRepository<ForumThreadEntity, UUID> {

    /**
     * One keyset page of threads sorted by the Reddit-style hot score, highest first, with the
     * thread {@code id} as a total-order tiebreaker. All three lenses converge on this one query:
     * {@code brandId} / {@code modelId} narrow by car; {@code topicId} narrows by topic via an
     * {@code EXISTS} against the junction. Pass {@code null} for a filter to leave it unapplied.
     *
     * <p>The cursor is the {@code (rankingScore, id)} of the last row of the previous page, or
     * {@code null} on the first page (then {@code firstPage = true}). Request {@code size + 1} rows
     * to detect a further page. Because {@code ranking_score} is mutable, this feed is only
     * eventually consistent across pages — an accepted trade-off for a ranked feed.
     *
     * <p>Author-deleted (anonymized) threads are <em>included</em>: deletion only hides the author,
     * the thread itself stays listed everywhere.
     */
    @Query("""
            select t
              from ForumThreadEntity t
             where (:brandId is null or t.brandId = :brandId)
               and (:modelId is null or t.modelId = :modelId)
               and (:topicId is null or exists (
                        select 1 from ForumThreadTopicEntity tt
                         where tt.id.threadId = t.id and tt.id.topicId = :topicId))
               and (:firstPage = true
                    or t.rankingScore < :cursorScore
                    or (t.rankingScore = :cursorScore and t.id < :cursorId))
             order by t.rankingScore desc, t.id desc
            """)
    List<ForumThreadEntity> findHotPage(@Param("brandId") UUID brandId,
                                        @Param("modelId") UUID modelId,
                                        @Param("topicId") String topicId,
                                        @Param("firstPage") boolean firstPage,
                                        @Param("cursorScore") Double cursorScore,
                                        @Param("cursorId") UUID cursorId,
                                        Pageable pageable);

    /**
     * One keyset page of threads sorted newest first ({@code created_at DESC, id DESC}). Same lens
     * filters and first-page semantics as {@link #findHotPage}.
     */
    @Query("""
            select t
              from ForumThreadEntity t
             where (:brandId is null or t.brandId = :brandId)
               and (:modelId is null or t.modelId = :modelId)
               and (:topicId is null or exists (
                        select 1 from ForumThreadTopicEntity tt
                         where tt.id.threadId = t.id and tt.id.topicId = :topicId))
               and (:firstPage = true
                    or t.createdAt < :cursorTs
                    or (t.createdAt = :cursorTs and t.id < :cursorId))
             order by t.createdAt desc, t.id desc
            """)
    List<ForumThreadEntity> findNewPage(@Param("brandId") UUID brandId,
                                        @Param("modelId") UUID modelId,
                                        @Param("topicId") String topicId,
                                        @Param("firstPage") boolean firstPage,
                                        @Param("cursorTs") Instant cursorTs,
                                        @Param("cursorId") UUID cursorId,
                                        Pageable pageable);

    /**
     * One keyset page of threads sorted by most-recent activity
     * ({@code last_activity_at DESC, id DESC}). Same lens filters and first-page semantics as
     * {@link #findHotPage}.
     */
    @Query("""
            select t
              from ForumThreadEntity t
             where (:brandId is null or t.brandId = :brandId)
               and (:modelId is null or t.modelId = :modelId)
               and (:topicId is null or exists (
                        select 1 from ForumThreadTopicEntity tt
                         where tt.id.threadId = t.id and tt.id.topicId = :topicId))
               and (:firstPage = true
                    or t.lastActivityAt < :cursorTs
                    or (t.lastActivityAt = :cursorTs and t.id < :cursorId))
             order by t.lastActivityAt desc, t.id desc
            """)
    List<ForumThreadEntity> findActivePage(@Param("brandId") UUID brandId,
                                           @Param("modelId") UUID modelId,
                                           @Param("topicId") String topicId,
                                           @Param("firstPage") boolean firstPage,
                                           @Param("cursorTs") Instant cursorTs,
                                           @Param("cursorId") UUID cursorId,
                                           Pageable pageable);

    /**
     * Of the given thread ids, those the viewer has liked — one query for a page's like flags.
     */
    @Query("""
            select l.id.threadId
              from ForumThreadLikeEntity l
             where l.id.userId = :userId and l.id.threadId in :threadIds
            """)
    List<UUID> findLikedThreadIds(@Param("userId") UUID userId, @Param("threadIds") Collection<UUID> threadIds);

    /**
     * The unread-thread count for a shortcut's badge: threads matching the shortcut's
     * brand/model/topic filter (same nullable-filter + topic-EXISTS pattern as the list queries),
     * created after {@code baseline} (the shortcut's own creation time, so its pre-existing backlog
     * is not counted), that the user has not yet opened (no {@code forum_thread_reads} row).
     */
    @Query("""
            select count(t)
              from ForumThreadEntity t
             where (:brandId is null or t.brandId = :brandId)
               and (:modelId is null or t.modelId = :modelId)
               and (:topicId is null or exists (
                        select 1 from ForumThreadTopicEntity tt
                         where tt.id.threadId = t.id and tt.id.topicId = :topicId))
               and t.createdAt > :baseline
               and not exists (
                        select 1 from ForumThreadReadEntity r
                         where r.id.threadId = t.id and r.id.userId = :userId)
            """)
    long countUnreadForShortcut(@Param("userId") UUID userId,
                                @Param("brandId") UUID brandId,
                                @Param("modelId") UUID modelId,
                                @Param("topicId") String topicId,
                                @Param("baseline") Instant baseline);
}
