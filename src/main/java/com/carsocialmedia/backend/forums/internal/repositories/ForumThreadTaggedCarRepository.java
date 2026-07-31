package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedCarEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedCarId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumThreadTaggedCarRepository
        extends JpaRepository<ForumThreadTaggedCarEntity, ForumThreadTaggedCarId> {

    List<ForumThreadTaggedCarEntity> findAllByIdThreadId(UUID threadId);

    /** Batch variant for assembling a page of thread cards; group by {@code id.threadId} on the caller side. */
    List<ForumThreadTaggedCarEntity> findAllByIdThreadIdIn(Collection<UUID> threadIds);

    void deleteAllByIdThreadId(UUID threadId);

    /**
     * Drops the given cars' tags on one thread — the car half of untagging yourself, keeping the
     * "a tagged car's owner is tagged too" invariant intact. Idempotent.
     */
    long deleteByIdThreadIdAndIdCarIdIn(UUID threadId, Collection<UUID> carIds);

    /**
     * One keyset page of the threads one of these cars is tagged in, most recently tagged first,
     * excluding threads started by {@code ownerId}. Same ordering and cursor scheme as
     * {@link ForumThreadTaggedPersonRepository#findTaggedThreadRefs}.
     */
    @Query("""
            select t.id as targetId,
                   cast(null as java.util.UUID) as parentId,
                   coalesce(tag.createdAt, t.createdAt) as taggedAt
              from ForumThreadTaggedCarEntity tag
              join ForumThreadEntity t on t.id = tag.id.threadId
             where tag.id.carId in :carIds
               and t.userId <> :ownerId
               and (:firstPage = true
                    or coalesce(tag.createdAt, t.createdAt) < :cursorTs
                    or (coalesce(tag.createdAt, t.createdAt) = :cursorTs and t.id < :cursorId))
             order by coalesce(tag.createdAt, t.createdAt) desc, t.id desc
            """)
    List<TagRefRow> findTaggedThreadRefs(@Param("carIds") Collection<UUID> carIds,
                                         @Param("ownerId") UUID ownerId,
                                         @Param("firstPage") boolean firstPage,
                                         @Param("cursorTs") Instant cursorTs,
                                         @Param("cursorId") UUID cursorId,
                                         Pageable pageable);
}
