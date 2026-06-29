package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.PostEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
     * One keyset page of the global feed: every post ranked by virality, highest first, with the
     * post {@code id} as a total-order tiebreaker. {@code ranking_score} is maintained by a
     * Supabase trigger from engagement and age. The cursor is the {@code (rankingScore, id)} of the
     * last row of the previous page, or {@code null} for the first page. Pass a {@link Pageable} of
     * {@code size + 1} to detect a further page.
     *
     * <p>Note: {@code ranking_score} is mutable (it shifts as a post gains engagement), so a post
     * may occasionally repeat or be skipped across pages — an accepted trade-off for a ranked
     * "most viral" feed.
     */
    @Query("""
            select p
              from PostEntity p
             where (:firstPage = true
                    or p.rankingScore < :cursorScore
                    or (p.rankingScore = :cursorScore and p.id < :cursorId))
             order by p.rankingScore desc, p.id desc
            """)
    List<PostEntity> findRankedPostPage(@Param("firstPage") boolean firstPage,
                                        @Param("cursorScore") Double cursorScore,
                                        @Param("cursorId") UUID cursorId,
                                        Pageable pageable);
}
