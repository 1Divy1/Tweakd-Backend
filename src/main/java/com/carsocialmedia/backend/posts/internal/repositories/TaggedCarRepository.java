package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.TaggedCarEntity;
import com.carsocialmedia.backend.posts.internal.entities.TaggedCarId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaggedCarRepository extends JpaRepository<TaggedCarEntity, TaggedCarId> {

    List<TaggedCarEntity> findAllByIdPostId(UUID postId);

    void deleteAllByIdPostId(UUID postId);
}
