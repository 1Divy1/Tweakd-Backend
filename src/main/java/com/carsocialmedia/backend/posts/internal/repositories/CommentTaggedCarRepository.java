package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.CommentTaggedCarEntity;
import com.carsocialmedia.backend.posts.internal.entities.CommentTaggedCarId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommentTaggedCarRepository
        extends JpaRepository<CommentTaggedCarEntity, CommentTaggedCarId> {

    /** Batch variant for assembling a page of comments; group by {@code id.commentId} on the caller side. */
    List<CommentTaggedCarEntity> findAllByIdCommentIdIn(Collection<UUID> commentIds);

    /**
     * Drops the given cars' tags on one comment — the car half of untagging yourself, keeping the
     * "a tagged car's owner is tagged too" invariant intact. Idempotent.
     */
    long deleteByIdCommentIdAndIdCarIdIn(UUID commentId, Collection<UUID> carIds);

    /**
     * One keyset page of the comments one of these cars is tagged in, most recently tagged first,
     * skipping comments written by {@code ownerId} and soft-deleted ones. Same ordering and cursor
     * scheme as {@link CommentTaggedPersonRepository#findTaggedCommentRefs}.
     */
    @Query("""
            select c.id as targetId,
                   c.postId as parentId,
                   coalesce(t.createdAt, c.createdAt) as taggedAt
              from CommentTaggedCarEntity t
              join CommentEntity c on c.id = t.id.commentId
             where t.id.carId in :carIds
               and c.userId <> :ownerId
               and c.deleted = false
               and (:firstPage = true
                    or coalesce(t.createdAt, c.createdAt) < :cursorTs
                    or (coalesce(t.createdAt, c.createdAt) = :cursorTs and c.id < :cursorId))
             order by coalesce(t.createdAt, c.createdAt) desc, c.id desc
            """)
    List<TagRefRow> findTaggedCommentRefs(@Param("carIds") Collection<UUID> carIds,
                                          @Param("ownerId") UUID ownerId,
                                          @Param("firstPage") boolean firstPage,
                                          @Param("cursorTs") Instant cursorTs,
                                          @Param("cursorId") UUID cursorId,
                                          Pageable pageable);
}
