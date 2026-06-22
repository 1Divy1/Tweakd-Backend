package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.PostImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostImageRepository extends JpaRepository<PostImageEntity, UUID> {

    List<PostImageEntity> findAllByPostIdOrderByDisplayOrderAsc(UUID postId);

    void deleteAllByPostId(UUID postId);
}
