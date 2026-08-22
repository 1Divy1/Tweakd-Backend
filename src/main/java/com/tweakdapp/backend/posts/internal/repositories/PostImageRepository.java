package com.tweakdapp.backend.posts.internal.repositories;

import com.tweakdapp.backend.posts.internal.entities.PostImageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PostImageRepository extends JpaRepository<PostImageEntity, UUID> {

    List<PostImageEntity> findAllByPostIdOrderByDisplayOrderAsc(UUID postId);

    /** Batch variant for assembling a page of posts; group by {@code postId} on the caller side. */
    List<PostImageEntity> findAllByPostIdInOrderByPostIdAscDisplayOrderAsc(Collection<UUID> postIds);

    void deleteAllByPostId(UUID postId);
}
