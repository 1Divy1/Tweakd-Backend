package com.tweakdapp.backend.posts.internal.repositories;

import com.tweakdapp.backend.posts.internal.entities.CommentTaggedPersonEntity;
import com.tweakdapp.backend.posts.internal.entities.CommentTaggedPersonId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CommentTaggedPersonRepository
        extends JpaRepository<CommentTaggedPersonEntity, CommentTaggedPersonId> {

    /** Batch variant for assembling a page of comments; group by {@code id.commentId} on the caller side. */
    List<CommentTaggedPersonEntity> findAllByIdCommentIdIn(Collection<UUID> commentIds);

    /** Drops one user's tag on one comment — the person half of untagging yourself. Idempotent. */
    long deleteByIdCommentIdAndIdUserId(UUID commentId, UUID userId);

    /**
     * One keyset page of the comments this user is tagged in, most recently tagged first. Skips
     * their own comments and soft-deleted ones (a deleted comment renders as "[deleted]" and drops
     * its tags, so it has nothing to show). {@code parentId} carries the comment's post, which the
     * tags module needs to render the item and deep-link into the thread.
     */
    @Query("""
            select c.id as targetId,
                   c.postId as parentId,
                   coalesce(t.createdAt, c.createdAt) as taggedAt
              from CommentTaggedPersonEntity t
              join CommentEntity c on c.id = t.id.commentId
             where t.id.userId = :userId
               and c.userId <> :userId
               and c.deleted = false
               and (:firstPage = true
                    or coalesce(t.createdAt, c.createdAt) < :cursorTs
                    or (coalesce(t.createdAt, c.createdAt) = :cursorTs and c.id < :cursorId))
             order by coalesce(t.createdAt, c.createdAt) desc, c.id desc
            """)
    List<TagRefRow> findTaggedCommentRefs(@Param("userId") UUID userId,
                                          @Param("firstPage") boolean firstPage,
                                          @Param("cursorTs") Instant cursorTs,
                                          @Param("cursorId") UUID cursorId,
                                          Pageable pageable);
}
