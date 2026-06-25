package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.TaggedPersonEntity;
import com.carsocialmedia.backend.posts.internal.entities.TaggedPersonId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaggedPersonRepository extends JpaRepository<TaggedPersonEntity, TaggedPersonId> {

    List<TaggedPersonEntity> findAllByIdPostId(UUID postId);

    /** Batch variant for assembling a page of posts; group by {@code id.postId} on the caller side. */
    List<TaggedPersonEntity> findAllByIdPostIdIn(Collection<UUID> postIds);

    void deleteAllByIdPostId(UUID postId);
}
