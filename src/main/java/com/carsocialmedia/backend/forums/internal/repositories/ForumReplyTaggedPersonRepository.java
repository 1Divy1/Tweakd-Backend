package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumReplyTaggedPersonEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumReplyTaggedPersonId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumReplyTaggedPersonRepository
        extends JpaRepository<ForumReplyTaggedPersonEntity, ForumReplyTaggedPersonId> {

    List<ForumReplyTaggedPersonEntity> findAllByIdReplyId(UUID replyId);

    /** Batch variant for assembling a page of replies; group by {@code id.replyId} on the caller side. */
    List<ForumReplyTaggedPersonEntity> findAllByIdReplyIdIn(Collection<UUID> replyIds);

    void deleteAllByIdReplyId(UUID replyId);
}
