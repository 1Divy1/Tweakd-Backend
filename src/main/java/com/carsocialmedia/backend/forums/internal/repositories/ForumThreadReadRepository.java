package com.carsocialmedia.backend.forums.internal.repositories;

import com.carsocialmedia.backend.forums.internal.entities.ForumThreadReadEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadReadId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ForumThreadReadRepository extends JpaRepository<ForumThreadReadEntity, ForumThreadReadId> {

    /**
     * Idempotent "mark seen": inserts the (user, thread) row, or does nothing if the user has
     * already opened this thread. {@code ON CONFLICT DO NOTHING} keeps re-opens cheap and race-safe.
     */
    @Modifying
    @Query(value = """
            insert into forum_thread_reads (user_id, thread_id)
            values (:userId, :threadId)
            on conflict do nothing
            """, nativeQuery = true)
    void insertIgnoringConflict(@Param("userId") UUID userId, @Param("threadId") UUID threadId);
}
