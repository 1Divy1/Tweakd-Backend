package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.SavedPostEntity;
import com.carsocialmedia.backend.posts.internal.entities.SavedPostId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SavedPostRepository extends JpaRepository<SavedPostEntity, SavedPostId> {

    List<SavedPostEntity> findAllByIdUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByIdPostIdAndIdUserId(UUID postId, UUID userId);

    void deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);

    /** Of the given posts, the IDs the viewer has saved — one query for the whole page. */
    @Query("""
            select sp.id.postId
              from SavedPostEntity sp
             where sp.id.userId = :viewerId
               and sp.id.postId in :postIds
            """)
    List<UUID> findSavedPostIds(@Param("viewerId") UUID viewerId,
                                @Param("postIds") Collection<UUID> postIds);
}
