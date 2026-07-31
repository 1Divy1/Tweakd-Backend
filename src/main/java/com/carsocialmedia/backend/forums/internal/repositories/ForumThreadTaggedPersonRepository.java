package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedPersonEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedPersonId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumThreadTaggedPersonRepository
        extends JpaRepository<ForumThreadTaggedPersonEntity, ForumThreadTaggedPersonId> {

    List<ForumThreadTaggedPersonEntity> findAllByIdThreadId(UUID threadId);

    /** Batch variant for assembling a page of thread cards; group by {@code id.threadId} on the caller side. */
    List<ForumThreadTaggedPersonEntity> findAllByIdThreadIdIn(Collection<UUID> threadIds);

    void deleteAllByIdThreadId(UUID threadId);

    /** Drops one user's tag on one thread — the person half of untagging yourself. Idempotent. */
    long deleteByIdThreadIdAndIdUserId(UUID threadId, UUID userId);

    /**
     * One keyset page of the threads this user is tagged in, most recently tagged first, excluding
     * threads they started themselves. Anonymized ("deleted") threads stay in — they keep their
     * title, body and replies, only the author is hidden.
     *
     * <p>Ordered by the tag's own timestamp with the thread id as a total-order tiebreaker; pass a
     * {@link Pageable} of {@code size + 1} to detect a further page.
     */
    @Query("""
            select t.id as targetId,
                   cast(null as java.util.UUID) as parentId,
                   coalesce(tag.createdAt, t.createdAt) as taggedAt
              from ForumThreadTaggedPersonEntity tag
              join ForumThreadEntity t on t.id = tag.id.threadId
             where tag.id.userId = :userId
               and t.userId <> :userId
               and (:firstPage = true
                    or coalesce(tag.createdAt, t.createdAt) < :cursorTs
                    or (coalesce(tag.createdAt, t.createdAt) = :cursorTs and t.id < :cursorId))
             order by coalesce(tag.createdAt, t.createdAt) desc, t.id desc
            """)
    List<TagRefRow> findTaggedThreadRefs(@Param("userId") UUID userId,
                                         @Param("firstPage") boolean firstPage,
                                         @Param("cursorTs") Instant cursorTs,
                                         @Param("cursorId") UUID cursorId,
                                         Pageable pageable);
}
