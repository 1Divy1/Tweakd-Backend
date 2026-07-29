package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedPersonEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedPersonId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumThreadTaggedPersonRepository
        extends JpaRepository<ForumThreadTaggedPersonEntity, ForumThreadTaggedPersonId> {

    List<ForumThreadTaggedPersonEntity> findAllByIdThreadId(UUID threadId);

    /** Batch variant for assembling a page of thread cards; group by {@code id.threadId} on the caller side. */
    List<ForumThreadTaggedPersonEntity> findAllByIdThreadIdIn(Collection<UUID> threadIds);

    void deleteAllByIdThreadId(UUID threadId);
}
