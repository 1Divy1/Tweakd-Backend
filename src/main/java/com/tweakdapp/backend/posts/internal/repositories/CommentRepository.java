package com.tweakdapp.backend.posts.internal.repositories;

import com.tweakdapp.backend.posts.internal.entities.CommentEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {

    List<CommentEntity> findAllByPostIdOrderByCreatedAtDesc(UUID postId);

    void deleteAllByPostId(UUID postId);

    /**
     * One keyset page of a post's <em>root</em> comments (replies are loaded per-thread
     * separately), ordered newest first with {@code id} as a total-order tiebreaker.
     *
     * <p>{@code cursorTs}/{@code cursorId} are the {@code (createdAt, id)} of the last row of
     * the previous page, or {@code null} for the first page. The predicate keeps rows strictly
     * older than the cursor, or equal-timestamp rows whose id sorts lower. Pass a {@link Pageable}
     * of {@code size + 1} to detect whether a further page exists.
     */
    @Query("""
            select c
              from CommentEntity c
             where c.postId = :postId
               and c.parentCommentId is null
               and (:firstPage = true
                    or c.createdAt < :cursorTs
                    or (c.createdAt = :cursorTs and c.id < :cursorId))
             order by c.createdAt desc, c.id desc
            """)
    List<CommentEntity> findRootCommentPage(@Param("postId") UUID postId,
                                            @Param("firstPage") boolean firstPage,
                                            @Param("cursorTs") Instant cursorTs,
                                            @Param("cursorId") UUID cursorId,
                                            Pageable pageable);

    /**
     * One keyset page of the replies to a single comment (its direct children), ordered newest
     * first with {@code id} as a total-order tiebreaker — same cursor scheme as
     * {@link #findRootCommentPage}. Pass a {@link Pageable} of {@code size + 1} to detect a
     * further page.
     */
    @Query("""
            select c
              from CommentEntity c
             where c.parentCommentId = :parentCommentId
               and (:firstPage = true
                    or c.createdAt < :cursorTs
                    or (c.createdAt = :cursorTs and c.id < :cursorId))
             order by c.createdAt desc, c.id desc
            """)
    List<CommentEntity> findReplyPage(@Param("parentCommentId") UUID parentCommentId,
                                      @Param("firstPage") boolean firstPage,
                                      @Param("cursorTs") Instant cursorTs,
                                      @Param("cursorId") UUID cursorId,
                                      Pageable pageable);
}
