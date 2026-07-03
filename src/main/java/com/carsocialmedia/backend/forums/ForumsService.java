package com.carsocialmedia.backend.forums;

import com.carsocialmedia.backend.forums.dto.CursorPage;
import com.carsocialmedia.backend.forums.dto.ReplyDto;
import com.carsocialmedia.backend.forums.dto.ShortcutDto;
import com.carsocialmedia.backend.forums.dto.ThreadCardDto;
import com.carsocialmedia.backend.forums.dto.ThreadDetailDto;
import com.carsocialmedia.backend.forums.dto.TopicGroupDto;
import com.carsocialmedia.backend.forums.dto.request.CreateReplyRequest;
import com.carsocialmedia.backend.forums.dto.request.CreateShortcutRequest;
import com.carsocialmedia.backend.forums.dto.request.CreateThreadRequest;
import com.carsocialmedia.backend.forums.dto.request.ReorderShortcutsRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateReplyRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateShortcutRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateThreadRequest;

import java.util.List;
import java.util.UUID;

/**
 * The forums feature: topic-and-car-scoped discussion threads with Reddit-style nested replies,
 * likes, and user-saved filters ("shortcuts").
 *
 * <p>Threads form a single pool reached through two lenses that converge on the same intersection:
 * <em>by car</em> (brand → model) and <em>by topic</em>. Every list supports three sorts — {@code hot}
 * (ranking score), {@code new} (creation time), {@code active} (last activity) — and paginates by
 * opaque keyset cursor.
 *
 * <p>All authorization is enforced here (the DB service role bypasses RLS): author-only
 * edit/delete, shortcut owner-scoping, and honoring {@code is_locked} on replies.
 */
public interface ForumsService {

    // ---- topics ------------------------------------------------------------

    /** The active topics, grouped by kind ({@code component} / {@code format}) for the picker. */
    List<TopicGroupDto> listTopics();

    // ---- read: feed & hubs (keyset pages of thread cards) ------------------

    /** The global thread feed across the whole app. */
    CursorPage<ThreadCardDto> getFeed(String currentUserId, String sort, String cursor, int size);

    /** Threads scoped to a brand (the brand hub). */
    CursorPage<ThreadCardDto> getBrandThreads(String currentUserId, UUID brandId, String sort, String cursor, int size);

    /** Threads scoped to a model (the model hub), optionally refined by a topic. */
    CursorPage<ThreadCardDto> getModelThreads(String currentUserId, UUID modelId, String sort, String topicId, String cursor, int size);

    /** Threads scoped to a topic (the topic hub), optionally refined by brand and/or model. */
    CursorPage<ThreadCardDto> getTopicThreads(String currentUserId, String topicId, String sort, UUID brandId, UUID modelId, String cursor, int size);

    // ---- read: thread detail & replies -------------------------------------

    /** A single thread's detail, including whether the current user liked it. */
    ThreadDetailDto getThread(String currentUserId, UUID threadId);

    /**
     * One keyset page of a thread's <em>top-level</em> replies, without children. The client
     * expands a reply's subtree on demand via {@link #getPostReplies}. Deleted replies are returned
     * as "[deleted]" placeholders (they may still anchor children).
     */
    CursorPage<ReplyDto> getReplies(String currentUserId, UUID threadId, String cursor, int size);

    /**
     * One keyset page of a reply's <em>direct children</em> — the expand-on-demand counterpart of
     * {@link #getReplies}, applied recursively for deeper levels.
     */
    CursorPage<ReplyDto> getPostReplies(String currentUserId, UUID postId, String cursor, int size);

    // ---- write: threads & replies ------------------------------------------

    /** Creates a thread (with its topic tags) and returns it hydrated with trigger-set values. */
    ThreadDetailDto createThread(String currentUserId, CreateThreadRequest request);

    /**
     * Edits the OP body of a thread the caller authored (Reddit-style: the title is immutable).
     * Rejected when the thread is deleted or locked.
     */
    ThreadDetailDto updateThread(String currentUserId, UUID threadId, UpdateThreadRequest request);

    /** Adds a reply to a thread (rejected if the thread is locked or the parent reply is deleted). */
    ReplyDto addReply(String currentUserId, UUID threadId, CreateReplyRequest request);

    /**
     * Edits the text of a reply the caller authored. Rejected when the reply is deleted or its
     * thread is locked.
     */
    ReplyDto updateReply(String currentUserId, UUID postId, UpdateReplyRequest request);

    // ---- write: likes (idempotent) -----------------------------------------

    void likeThread(String currentUserId, UUID threadId);

    void unlikeThread(String currentUserId, UUID threadId);

    void likePost(String currentUserId, UUID postId);

    void unlikePost(String currentUserId, UUID postId);

    // ---- write: delete (author-only) ----------------------------------------

    /**
     * Author-only. A thread with replies is <em>anonymized</em> (author hidden; title, body, and
     * reply tree stay fully visible and repliable); a thread with no replies is removed outright.
     */
    void deleteThread(String currentUserId, UUID threadId);

    /** Author-only. Soft-deletes a reply that has children ("[deleted]" placeholder), else removes it. */
    void deletePost(String currentUserId, UUID postId);

    // ---- shortcuts (owner-scoped) ------------------------------------------

    List<ShortcutDto> listShortcuts(String currentUserId);

    ShortcutDto createShortcut(String currentUserId, CreateShortcutRequest request);

    ShortcutDto updateShortcut(String currentUserId, UUID shortcutId, UpdateShortcutRequest request);

    void deleteShortcut(String currentUserId, UUID shortcutId);

    /** Reorders the user's shortcuts to match the given id order; returns the reordered list. */
    List<ShortcutDto> reorderShortcuts(String currentUserId, ReorderShortcutsRequest request);
}
