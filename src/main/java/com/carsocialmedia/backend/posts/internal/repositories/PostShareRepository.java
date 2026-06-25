package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.PostShareEntity;
import com.carsocialmedia.backend.posts.internal.entities.PostShareId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostShareRepository extends JpaRepository<PostShareEntity, PostShareId> {

    boolean existsByIdPostIdAndIdUserId(UUID postId, UUID userId);
}
