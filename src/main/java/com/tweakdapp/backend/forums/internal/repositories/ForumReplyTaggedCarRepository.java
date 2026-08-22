package com.tweakdapp.backend.forums.internal.repositories;

import com.tweakdapp.backend.forums.internal.entities.ForumReplyTaggedCarEntity;
import com.tweakdapp.backend.forums.internal.entities.ForumReplyTaggedCarId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumReplyTaggedCarRepository
        extends JpaRepository<ForumReplyTaggedCarEntity, ForumReplyTaggedCarId> {

    List<ForumReplyTaggedCarEntity> findAllByIdReplyId(UUID replyId);

    /** Batch variant for assembling a page of replies; group by {@code id.replyId} on the caller side. */
    List<ForumReplyTaggedCarEntity> findAllByIdReplyIdIn(Collection<UUID> replyIds);

    void deleteAllByIdReplyId(UUID replyId);

    /**
     * Drops the given cars' tags on one reply — the car half of untagging yourself, keeping the
     * "a tagged car's owner is tagged too" invariant intact. Idempotent.
     */
    long deleteByIdReplyIdAndIdCarIdIn(UUID replyId, Collection<UUID> carIds);

    /**
     * One keyset page of the thread replies one of these cars is tagged in, most recently tagged
     * first, skipping replies written by {@code ownerId} and soft-deleted ones. Same ordering and
     * cursor scheme as {@link ForumReplyTaggedPersonRepository#findTaggedReplyRefs}.
     */
    @Query("""
            select r.id as targetId,
                   r.threadId as parentId,
                   coalesce(tag.createdAt, r.createdAt) as taggedAt
              from ForumReplyTaggedCarEntity tag
              join ForumThreadReplyEntity r on r.id = tag.id.replyId
             where tag.id.carId in :carIds
               and r.userId <> :ownerId
               and r.deleted = false
               and (:firstPage = true
                    or coalesce(tag.createdAt, r.createdAt) < :cursorTs
                    or (coalesce(tag.createdAt, r.createdAt) = :cursorTs and r.id < :cursorId))
             order by coalesce(tag.createdAt, r.createdAt) desc, r.id desc
            """)
    List<TagRefRow> findTaggedReplyRefs(@Param("carIds") Collection<UUID> carIds,
                                        @Param("ownerId") UUID ownerId,
                                        @Param("firstPage") boolean firstPage,
                                        @Param("cursorTs") Instant cursorTs,
                                        @Param("cursorId") UUID cursorId,
                                        Pageable pageable);
}
