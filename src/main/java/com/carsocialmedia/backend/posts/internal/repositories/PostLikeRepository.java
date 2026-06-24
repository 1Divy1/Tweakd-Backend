package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.PostLikeEntity;
import com.carsocialmedia.backend.posts.internal.entities.PostLikeId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLikeEntity, PostLikeId> {

    boolean existsByIdPostIdAndIdUserId(UUID postId, UUID userId);

    void deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);

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
               and (:cursorTs is null
                    or pl.createdAt < :cursorTs
                    or (pl.createdAt = :cursorTs and pl.id.userId < :cursorUserId))
             order by pl.createdAt desc, pl.id.userId desc
            """)
    List<PostLikeEntity> findLikerPage(@Param("postId") UUID postId,
                                       @Param("cursorTs") Instant cursorTs,
                                       @Param("cursorUserId") UUID cursorUserId,
                                       Pageable pageable);
}
