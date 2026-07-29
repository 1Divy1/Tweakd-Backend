package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumReplyTaggedCarEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumReplyTaggedCarId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumReplyTaggedCarRepository
        extends JpaRepository<ForumReplyTaggedCarEntity, ForumReplyTaggedCarId> {

    List<ForumReplyTaggedCarEntity> findAllByIdReplyId(UUID replyId);

    /** Batch variant for assembling a page of replies; group by {@code id.replyId} on the caller side. */
    List<ForumReplyTaggedCarEntity> findAllByIdReplyIdIn(Collection<UUID> replyIds);

    void deleteAllByIdReplyId(UUID replyId);
}
