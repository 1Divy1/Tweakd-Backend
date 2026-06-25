package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.TaggedCarEntity;
import com.carsocialmedia.backend.posts.internal.entities.TaggedCarId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaggedCarRepository extends JpaRepository<TaggedCarEntity, TaggedCarId> {

    List<TaggedCarEntity> findAllByIdPostId(UUID postId);

    /** Batch variant for assembling a page of posts; group by {@code id.postId} on the caller side. */
    List<TaggedCarEntity> findAllByIdPostIdIn(Collection<UUID> postIds);

    void deleteAllByIdPostId(UUID postId);
}
