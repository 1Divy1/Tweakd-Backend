package com.tweakdapp.backend.posts.internal.repositories;

import com.tweakdapp.backend.posts.internal.entities.SavedPostEntity;
import com.tweakdapp.backend.posts.internal.entities.SavedPostId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * One keyset page of a user's saved posts, ordered by when they were saved (newest save first)
     * with the post {@code id} as a total-order tiebreaker. The cursor is the
     * {@code (createdAt, postId)} of the last row of the previous page, or {@code null} for the
     * first page. Pass a {@link Pageable} of {@code size + 1} to detect a further page.
     */
    @Query("""
            select sp
              from SavedPostEntity sp
             where sp.id.userId = :userId
               and not exists (select 1 from PostEntity p
                                where p.id = sp.id.postId and p.userId in :hiddenIds)
               and (:firstPage = true
                    or sp.createdAt < :cursorTs
                    or (sp.createdAt = :cursorTs and sp.id.postId < :cursorPostId))
             order by sp.createdAt desc, sp.id.postId desc
            """)
    List<SavedPostEntity> findSavedPage(@Param("userId") UUID userId,
                                        @Param("firstPage") boolean firstPage,
                                        @Param("cursorTs") Instant cursorTs,
                                        @Param("cursorPostId") UUID cursorPostId,
                                        @Param("hiddenIds") Collection<UUID> hiddenIds,
                                        Pageable pageable);
}
