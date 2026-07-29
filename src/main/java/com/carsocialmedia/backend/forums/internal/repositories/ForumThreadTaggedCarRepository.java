package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedCarEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedCarId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumThreadTaggedCarRepository
        extends JpaRepository<ForumThreadTaggedCarEntity, ForumThreadTaggedCarId> {

    List<ForumThreadTaggedCarEntity> findAllByIdThreadId(UUID threadId);

    /** Batch variant for assembling a page of thread cards; group by {@code id.threadId} on the caller side. */
    List<ForumThreadTaggedCarEntity> findAllByIdThreadIdIn(Collection<UUID> threadIds);

    void deleteAllByIdThreadId(UUID threadId);
}
