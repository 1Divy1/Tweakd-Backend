package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumPostEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumPostRepository extends JpaRepository<ForumPostEntity, UUID> {

    /**
     * One keyset page of a thread's <em>top-level</em> replies (parent is null), oldest first so a
     * thread reads top-to-bottom, with the reply {@code id} as a total-order tiebreaker. The cursor
     * is the {@code (createdAt, id)} of the last row of the previous page, or {@code null} on the
     * first page (then {@code firstPage = true}). Request {@code size + 1} rows to detect a further
     * page.
     *
     * <p>Deleted roots are kept (rendered "[deleted]") because they may still anchor visible
     * children.
     */
    @Query("""
            select p
              from ForumPostEntity p
             where p.threadId = :threadId
               and p.parentPostId is null
               and (:firstPage = true
                    or p.createdAt > :cursorTs
                    or (p.createdAt = :cursorTs and p.id > :cursorId))
             order by p.createdAt asc, p.id asc
            """)
    List<ForumPostEntity> findRootReplyPage(@Param("threadId") UUID threadId,
                                            @Param("firstPage") boolean firstPage,
                                            @Param("cursorTs") Instant cursorTs,
                                            @Param("cursorId") UUID cursorId,
                                            Pageable pageable);

    /**
     * Newest-first counterpart of {@link #findRootReplyPage} ({@code created_at DESC, id DESC}). Same
     * {@code (createdAt, id)} cursor and {@code size + 1} semantics; only the comparison direction is
     * reversed.
     */
    @Query("""
            select p
              from ForumPostEntity p
             where p.threadId = :threadId
               and p.parentPostId is null
               and (:firstPage = true
                    or p.createdAt < :cursorTs
                    or (p.createdAt = :cursorTs and p.id < :cursorId))
             order by p.createdAt desc, p.id desc
            """)
    List<ForumPostEntity> findRootReplyPageDesc(@Param("threadId") UUID threadId,
                                                @Param("firstPage") boolean firstPage,
                                                @Param("cursorTs") Instant cursorTs,
                                                @Param("cursorId") UUID cursorId,
                                                Pageable pageable);

    /**
     * One keyset page of a reply's <em>direct children</em>, oldest first — the expand-on-demand
     * counterpart of {@link #findRootReplyPage}: the client fetches a level of the tree only when
     * the user expands its parent. Same cursor and {@code size + 1} semantics.
     */
    @Query("""
            select p
              from ForumPostEntity p
             where p.parentPostId = :parentId
               and (:firstPage = true
                    or p.createdAt > :cursorTs
                    or (p.createdAt = :cursorTs and p.id > :cursorId))
             order by p.createdAt asc, p.id asc
            """)
    List<ForumPostEntity> findChildReplyPage(@Param("parentId") UUID parentId,
                                             @Param("firstPage") boolean firstPage,
                                             @Param("cursorTs") Instant cursorTs,
                                             @Param("cursorId") UUID cursorId,
                                             Pageable pageable);

    /**
     * Newest-first counterpart of {@link #findChildReplyPage} ({@code created_at DESC, id DESC}).
     */
    @Query("""
            select p
              from ForumPostEntity p
             where p.parentPostId = :parentId
               and (:firstPage = true
                    or p.createdAt < :cursorTs
                    or (p.createdAt = :cursorTs and p.id < :cursorId))
             order by p.createdAt desc, p.id desc
            """)
    List<ForumPostEntity> findChildReplyPageDesc(@Param("parentId") UUID parentId,
                                                 @Param("firstPage") boolean firstPage,
                                                 @Param("cursorTs") Instant cursorTs,
                                                 @Param("cursorId") UUID cursorId,
                                                 Pageable pageable);

    /** Whether the given reply still has any direct child — used to collapse deleted leaf chains. */
    boolean existsByParentPostId(UUID parentPostId);

    /**
     * Of the given reply ids, those the viewer has liked — one query for a page's like flags.
     */
    @Query("""
            select l.id.postId
              from ForumPostLikeEntity l
             where l.id.userId = :userId and l.id.postId in :postIds
            """)
    List<UUID> findLikedPostIds(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);
}
