package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumReplyTaggedPersonEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumReplyTaggedPersonId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumReplyTaggedPersonRepository
        extends JpaRepository<ForumReplyTaggedPersonEntity, ForumReplyTaggedPersonId> {

    List<ForumReplyTaggedPersonEntity> findAllByIdReplyId(UUID replyId);

    /** Batch variant for assembling a page of replies; group by {@code id.replyId} on the caller side. */
    List<ForumReplyTaggedPersonEntity> findAllByIdReplyIdIn(Collection<UUID> replyIds);

    void deleteAllByIdReplyId(UUID replyId);

    /** Drops one user's tag on one reply — the person half of untagging yourself. Idempotent. */
    long deleteByIdReplyIdAndIdUserId(UUID replyId, UUID userId);

    /**
     * One keyset page of the thread replies this user is tagged in, most recently tagged first.
     * Skips their own replies and soft-deleted ones (a deleted reply is a "[deleted]" placeholder
     * with no content or tags). {@code parentId} carries the reply's thread, which the tags module
     * needs to render the item and deep-link into it.
     */
    @Query("""
            select r.id as targetId,
                   r.threadId as parentId,
                   coalesce(tag.createdAt, r.createdAt) as taggedAt
              from ForumReplyTaggedPersonEntity tag
              join ForumThreadReplyEntity r on r.id = tag.id.replyId
             where tag.id.userId = :userId
               and r.userId <> :userId
               and r.deleted = false
               and (:firstPage = true
                    or coalesce(tag.createdAt, r.createdAt) < :cursorTs
                    or (coalesce(tag.createdAt, r.createdAt) = :cursorTs and r.id < :cursorId))
             order by coalesce(tag.createdAt, r.createdAt) desc, r.id desc
            """)
    List<TagRefRow> findTaggedReplyRefs(@Param("userId") UUID userId,
                                        @Param("firstPage") boolean firstPage,
                                        @Param("cursorTs") Instant cursorTs,
                                        @Param("cursorId") UUID cursorId,
                                        Pageable pageable);
}
