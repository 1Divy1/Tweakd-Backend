package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.TaggedCarEntity;
import com.carsocialmedia.backend.posts.internal.entities.TaggedCarId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaggedCarRepository extends JpaRepository<TaggedCarEntity, TaggedCarId> {

    List<TaggedCarEntity> findAllByIdPostId(UUID postId);

    /** Batch variant for assembling a page of posts; group by {@code id.postId} on the caller side. */
    List<TaggedCarEntity> findAllByIdPostIdIn(Collection<UUID> postIds);

    void deleteAllByIdPostId(UUID postId);

    /**
     * Drops the given cars' tags on one post — the car half of untagging yourself. Called with the
     * untagging user's own car ids so the "a tagged car's owner is tagged too" invariant survives
     * the removal of their person tag. Idempotent.
     */
    long deleteByIdPostIdAndIdCarIdIn(UUID postId, Collection<UUID> carIds);

    /**
     * One keyset page of the posts one of these cars is tagged in, most recently tagged first,
     * excluding posts written by {@code ownerId} (a user tagging their own car in their own post).
     * Same ordering and cursor scheme as {@link TaggedPersonRepository#findTaggedPostRefs}.
     */
    @Query("""
            select p.id as targetId,
                   cast(null as java.util.UUID) as parentId,
                   coalesce(t.createdAt, p.createdAt) as taggedAt
              from TaggedCarEntity t
              join PostEntity p on p.id = t.id.postId
             where t.id.carId in :carIds
               and p.userId <> :ownerId
               and (:firstPage = true
                    or coalesce(t.createdAt, p.createdAt) < :cursorTs
                    or (coalesce(t.createdAt, p.createdAt) = :cursorTs and p.id < :cursorId))
             order by coalesce(t.createdAt, p.createdAt) desc, p.id desc
            """)
    List<TagRefRow> findTaggedPostRefs(@Param("carIds") Collection<UUID> carIds,
                                       @Param("ownerId") UUID ownerId,
                                       @Param("firstPage") boolean firstPage,
                                       @Param("cursorTs") Instant cursorTs,
                                       @Param("cursorId") UUID cursorId,
                                       Pageable pageable);
}
