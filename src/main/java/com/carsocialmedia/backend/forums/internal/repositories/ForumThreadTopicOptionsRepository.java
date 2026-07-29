package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTopicOptionsEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Read-only reference data for {@code forum_thread_topic_options}.
 */
public interface ForumThreadTopicOptionsRepository extends JpaRepository<ForumThreadTopicOptionsEntity, String> {

    /**
     * Active topics for the picker, in the curated sort order.
     */
    List<ForumThreadTopicOptionsEntity> findByActiveTrueOrderBySortOrderAsc();

    /**
     * The most-discussed active topics, highest thread count first (excluding topics with no
     * threads). Backs the forums "popular hubs" suggestions.
     */
    List<ForumThreadTopicOptionsEntity> findByActiveTrueAndThreadCountGreaterThanOrderByThreadCountDesc(int minThreadCount, Pageable pageable);
}
