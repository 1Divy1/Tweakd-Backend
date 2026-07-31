package com.carsocialmedia.backend.posts.internal.repositories;

import com.carsocialmedia.backend.posts.internal.entities.TaggedPersonEntity;
import com.carsocialmedia.backend.posts.internal.entities.TaggedPersonId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaggedPersonRepository extends JpaRepository<TaggedPersonEntity, TaggedPersonId> {

    List<TaggedPersonEntity> findAllByIdPostId(UUID postId);

    /** Batch variant for assembling a page of posts; group by {@code id.postId} on the caller side. */
    List<TaggedPersonEntity> findAllByIdPostIdIn(Collection<UUID> postIds);

    void deleteAllByIdPostId(UUID postId);

    /** Drops one user's tag on one post — the person half of untagging yourself. Idempotent. */
    long deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);

    /**
     * One keyset page of the posts this user is tagged in, most recently tagged first, excluding
     * posts they wrote themselves (those already live under their posts tab).
     *
     * <p>Ordered by the tag's own timestamp — a tag added today on an old post surfaces as new —
     * with the post id as a total-order tiebreaker. {@code created_at} on this table is nullable
     * for historical rows, so it falls back to the post's creation time. Pass a {@link Pageable}
     * of {@code size + 1} to detect a further page.
     */
    @Query("""
            select p.id as targetId,
                   cast(null as java.util.UUID) as parentId,
                   coalesce(t.createdAt, p.createdAt) as taggedAt
              from TaggedPersonEntity t
              join PostEntity p on p.id = t.id.postId
             where t.id.userId = :userId
               and p.userId <> :userId
               and (:firstPage = true
                    or coalesce(t.createdAt, p.createdAt) < :cursorTs
                    or (coalesce(t.createdAt, p.createdAt) = :cursorTs and p.id < :cursorId))
             order by coalesce(t.createdAt, p.createdAt) desc, p.id desc
            """)
    List<TagRefRow> findTaggedPostRefs(@Param("userId") UUID userId,
                                       @Param("firstPage") boolean firstPage,
                                       @Param("cursorTs") Instant cursorTs,
                                       @Param("cursorId") UUID cursorId,
                                       Pageable pageable);
}
