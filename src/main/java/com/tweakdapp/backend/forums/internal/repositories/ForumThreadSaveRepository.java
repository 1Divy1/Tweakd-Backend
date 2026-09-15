package com.tweakdapp.backend.forums.internal.repositories;

import com.tweakdapp.backend.forums.internal.entities.ForumThreadSaveEntity;
import com.tweakdapp.backend.forums.internal.entities.ForumThreadSaveId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ForumThreadSaveRepository extends JpaRepository<ForumThreadSaveEntity, ForumThreadSaveId> {

    /**
     * Idempotent save: inserts the (thread, user) row, or does nothing if it already exists.
     * {@code ON CONFLICT DO NOTHING} makes concurrent double-taps race-safe on the composite PK.
     */
    @Modifying
    @Query(value = """
            insert into forum_thread_saves (thread_id, user_id)
            values (:threadId, :userId)
            on conflict do nothing
            """, nativeQuery = true)
    void insertIgnoringConflict(@Param("threadId") UUID threadId, @Param("userId") UUID userId);

    void deleteByIdThreadIdAndIdUserId(UUID threadId, UUID userId);

    /** Of the given thread ids, those the viewer has saved — one query for a page's saved flags. */
    @Query("""
            select s.id.threadId
              from ForumThreadSaveEntity s
             where s.id.userId = :userId and s.id.threadId in :threadIds
            """)
    List<UUID> findSavedThreadIds(@Param("userId") UUID userId, @Param("threadIds") Collection<UUID> threadIds);

    /**
     * One keyset page of the user's saves, newest save first ({@code created_at DESC, thread_id DESC}),
     * with the thread id as a total-order tiebreaker. The cursor is the {@code (createdAt, threadId)}
     * of the last row of the previous page, or {@code null} on the first page. Request {@code size + 1}
     * rows to detect a further page.
     */
    @Query("""
            select s
              from ForumThreadSaveEntity s
             where s.id.userId = :userId
               and not exists (select 1 from ForumThreadEntity t
                                where t.id = s.id.threadId and t.userId in :hiddenIds)
               and (:firstPage = true
                    or s.createdAt < :cursorTs
                    or (s.createdAt = :cursorTs and s.id.threadId < :cursorId))
             order by s.createdAt desc, s.id.threadId desc
            """)
    List<ForumThreadSaveEntity> findSavedPage(@Param("userId") UUID userId,
                                              @Param("firstPage") boolean firstPage,
                                              @Param("cursorTs") Instant cursorTs,
                                              @Param("cursorId") UUID cursorId,
                                              @Param("hiddenIds") Collection<UUID> hiddenIds,
                                              Pageable pageable);
}
