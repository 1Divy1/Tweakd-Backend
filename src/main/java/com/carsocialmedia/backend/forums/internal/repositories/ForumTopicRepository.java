package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumTopicEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Read-only reference data for {@code forum_topics}.
 */
public interface ForumTopicRepository extends JpaRepository<ForumTopicEntity, String> {

    /**
     * Active topics for the picker, grouped-ready: ordered by kind then the curated sort order.
     */
    List<ForumTopicEntity> findByActiveTrueOrderByKindAscSortOrderAsc();

    /**
     * The most-discussed active topics, highest thread count first (excluding topics with no
     * threads). Backs the forums "popular hubs" suggestions.
     */
    List<ForumTopicEntity> findByActiveTrueAndThreadCountGreaterThanOrderByThreadCountDesc(int minThreadCount, Pageable pageable);
}
