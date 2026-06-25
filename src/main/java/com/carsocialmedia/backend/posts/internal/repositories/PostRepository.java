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
     * tiebreaker. The cursor is the {@code (createdAt, id)} of the last row of the previous page,
     * or {@code null} for the first page. Pass a {@link Pageable} of {@code size + 1} to detect a
     * further page.
     */
    @Query("""
            select p
              from PostEntity p
             where p.userId = :userId
               and (:cursorTs is null
                    or p.createdAt < :cursorTs
                    or (p.createdAt = :cursorTs and p.id < :cursorId))
             order by p.createdAt desc, p.id desc
            """)
    List<PostEntity> findUserPostPage(@Param("userId") UUID userId,
                                      @Param("cursorTs") Instant cursorTs,
                                      @Param("cursorId") UUID cursorId,
                                      Pageable pageable);
}
