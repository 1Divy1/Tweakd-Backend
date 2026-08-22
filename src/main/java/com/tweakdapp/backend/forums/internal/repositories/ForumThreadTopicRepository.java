package com.tweakdapp.backend.forums.internal.repositories;

import com.tweakdapp.backend.forums.internal.entities.ForumThreadTopicEntity;
import com.tweakdapp.backend.forums.internal.entities.ForumThreadTopicId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumThreadTopicRepository extends JpaRepository<ForumThreadTopicEntity, ForumThreadTopicId> {

    /** All topic tags for the given threads — one query to hydrate a page of thread cards. */
    List<ForumThreadTopicEntity> findByIdThreadIdIn(Collection<UUID> threadIds);
}
