package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.SavedPostEntity;
import com.carsocialmedia.backend.posts.internal.entities.SavedPostId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SavedPostRepository extends JpaRepository<SavedPostEntity, SavedPostId> {

    List<SavedPostEntity> findAllByIdUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByIdPostIdAndIdUserId(UUID postId, UUID userId);

    void deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);
}
