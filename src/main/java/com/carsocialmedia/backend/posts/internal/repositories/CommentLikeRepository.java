package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.CommentLikeEntity;
import com.carsocialmedia.backend.posts.internal.entities.CommentLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommentLikeRepository extends JpaRepository<CommentLikeEntity, CommentLikeId> {

    boolean existsByIdCommentIdAndIdUserId(UUID commentId, UUID userId);

    void deleteByIdCommentIdAndIdUserId(UUID commentId, UUID userId);

    /**
     * Of the given comments, the IDs the viewer has liked — one query for the whole page.
     * (The like <em>count</em> is read from the denormalized {@code comments.likes_count}.)
     */
    @Query("""
            select cl.id.commentId
              from CommentLikeEntity cl
             where cl.id.userId = :viewerId
               and cl.id.commentId in :commentIds
            """)
    List<UUID> findLikedCommentIds(@Param("viewerId") UUID viewerId,
                                   @Param("commentIds") Collection<UUID> commentIds);
}
