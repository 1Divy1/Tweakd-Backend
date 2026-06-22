package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.CommentLikeEntity;
import com.carsocialmedia.backend.posts.internal.entities.CommentLikeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CommentLikeRepository extends JpaRepository<CommentLikeEntity, CommentLikeId> {

    boolean existsByIdCommentIdAndIdUserId(UUID commentId, UUID userId);

    void deleteByIdCommentIdAndIdUserId(UUID commentId, UUID userId);
}
