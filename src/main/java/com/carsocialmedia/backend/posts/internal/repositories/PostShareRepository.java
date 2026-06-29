package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.PostShareEntity;
import com.carsocialmedia.backend.posts.internal.entities.PostShareId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PostShareRepository extends JpaRepository<PostShareEntity, PostShareId> {

    boolean existsByIdPostIdAndIdUserId(UUID postId, UUID userId);

    void deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);

    /**
     * One keyset page of a user's shared posts, ordered by when they were shared (newest share
     * first) with the post {@code id} as a total-order tiebreaker. The cursor is the
     * {@code (createdAt, postId)} of the last row of the previous page, or {@code null} for the
     * first page. Pass a {@link Pageable} of {@code size + 1} to detect a further page.
     */
    @Query("""
            select ps
              from PostShareEntity ps
             where ps.id.userId = :userId
               and (:firstPage = true
                    or ps.createdAt < :cursorTs
                    or (ps.createdAt = :cursorTs and ps.id.postId < :cursorPostId))
             order by ps.createdAt desc, ps.id.postId desc
            """)
    List<PostShareEntity> findSharedPage(@Param("userId") UUID userId,
                                         @Param("firstPage") boolean firstPage,
                                         @Param("cursorTs") Instant cursorTs,
                                         @Param("cursorPostId") UUID cursorPostId,
                                         Pageable pageable);
}
