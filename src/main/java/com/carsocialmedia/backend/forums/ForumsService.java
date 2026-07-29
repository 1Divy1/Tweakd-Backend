package com.carsocialmedia.backend.forums;

import com.carsocialmedia.backend.forums.dto.CursorPage;
import com.carsocialmedia.backend.forums.dto.ForumSuggestionDto;
import com.carsocialmedia.backend.forums.dto.ReplyDto;
import com.carsocialmedia.backend.forums.dto.ShortcutDto;
import com.carsocialmedia.backend.forums.dto.ThreadCardDto;
import com.carsocialmedia.backend.forums.dto.ThreadDetailDto;
import com.carsocialmedia.backend.forums.dto.TopicDto;
import com.carsocialmedia.backend.forums.dto.request.CreateReplyRequest;
import com.carsocialmedia.backend.forums.dto.request.CreateShortcutRequest;
import com.carsocialmedia.backend.forums.dto.request.CreateThreadRequest;
import com.carsocialmedia.backend.forums.dto.request.ReorderShortcutsRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateReplyRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateShortcutRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateThreadRequest;
import com.carsocialmedia.backend.report.dto.ReportReasonDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The forums feature: topic-and-car-scoped discussion threads with Reddit-style nested replies,
 * likes, and user-saved filters ("shortcuts").
 *
 * <p>Threads form a single pool reached primarily <em>by car</em> (brand → model), optionally
 * refined <em>by topic</em> within a brand or brand+model hub (never a topic-only feed). Every list supports three sorts — {@code hot}
 * (ranking score), {@code new} (creation time), {@code active} (last activity) — and paginates by
 * opaque keyset cursor.
 *
 * <p>All authorization is enforced here (the DB service role bypasses RLS): author-only
 * edit/delete, shortcut owner-scoping, and honoring {@code is_locked} on replies.
 */
public interface ForumsService {

    // ---- topics ------------------------------------------------------------

    /** The active topics, in curated order, for the picker. */
    List<TopicDto> listTopics();

    /**
     * Popular-hub suggestions for the forums discovery / empty state — the most active brands and
     * models merged into one list, ranked by thread count.
     *
     * @param limit the maximum number of suggestions to return (defaulted/capped by the service)
     */
    List<ForumSuggestionDto> getSuggestions(int limit);

    // ---- read: feed & hubs (keyset pages of thread cards) ------------------

    /** The global thread feed across the whole app. */
    CursorPage<ThreadCardDto> getFeed(String currentUserId, String sort, String cursor, int size);

    /** Threads scoped to a brand (the brand hub), optionally refined by a topic. */
    CursorPage<ThreadCardDto> getBrandThreads(String currentUserId, UUID brandId, String sort, String topicId, String cursor, int size);

    /** Threads scoped to a model (the model hub), optionally refined by a topic. */
    CursorPage<ThreadCardDto> getModelThreads(String currentUserId, UUID modelId, String sort, String topicId, String cursor, int size);

    // ---- read: thread detail & replies -------------------------------------

    /** A single thread's detail, including whether the current user liked it. */
    ThreadDetailDto getThread(String currentUserId, UUID threadId);

    /**
     * One keyset page of a thread's <em>top-level</em> replies, without children. The client
     * expands a reply's subtree on demand via {@link #getPostReplies}. Deleted replies are returned
     * as "[deleted]" placeholders (they may still anchor children).
     *
     * @param sort {@code old} (default, oldest first) or {@code new} (newest first)
     */
    CursorPage<ReplyDto> getReplies(String currentUserId, UUID threadId, String sort, String cursor, int size);

    /**
     * One keyset page of a reply's <em>direct children</em> — the expand-on-demand counterpart of
     * {@link #getReplies}, applied recursively for deeper levels.
     *
     * @param sort {@code old} (default, oldest first) or {@code new} (newest first)
     */
    CursorPage<ReplyDto> getPostReplies(String currentUserId, UUID postId, String sort, String cursor, int size);

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

    // ---- saves (bookmarks, owner-scoped) -----------------------------------

    /** Saves (bookmarks) a thread for the current user. Idempotent. */
    void saveThread(String currentUserId, UUID threadId);

    /** Removes the current user's save of a thread. Idempotent. */
    void unsaveThread(String currentUserId, UUID threadId);

    /** One keyset page of the threads the current user has saved, newest save first. */
    CursorPage<ThreadCardDto> getSavedThreads(String currentUserId, String cursor, int size);

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

    // ---- reporting (delegates to the report module) ------------------------

    /**
     * Reports a thread. Validates the thread exists and rejects a self-report
     * ({@code CannotReportOwnForumContentException}); reason and duplicate validation are delegated
     * to the report module.
     */
    void reportThread(String currentUserId, UUID threadId, UUID reasonId);

    /**
     * Reports a reply. Validates the reply exists and rejects a self-report; reason and duplicate
     * validation are delegated to the report module.
     */
    void reportReply(String currentUserId, UUID postId, UUID reasonId);

    /** The preset reasons a user may pick from when reporting a thread. */
    List<ReportReasonDto> listThreadReportReasons();

    /** The preset reasons a user may pick from when reporting a reply. */
    List<ReportReasonDto> listReplyReportReasons();

    // ---- moderation (called by the admin module; no auth logic here) --------

    /**
     * The reported thread in the uniform moderation shape ({@code content} is the title and body
     * joined with a blank line), or empty if the thread is gone. Empty rather than an exception on
     * purpose: the caller runs inside its own transaction, and a not-found thrown across that
     * boundary would mark it rollback-only.
     */
    Optional<com.carsocialmedia.backend.shared.moderation.ModerationContentDto> findThreadModerationSnapshot(UUID threadId);

    /**
     * The reported reply in the uniform moderation shape, or empty if the reply is gone (see
     * {@link #findThreadModerationSnapshot}).
     */
    Optional<com.carsocialmedia.backend.shared.moderation.ModerationContentDto> findReplyModerationSnapshot(UUID postId);

    /**
     * Deletes a thread on a moderator's behalf: same anonymize-with-replies / hard-delete-without
     * semantics as {@link #deleteThread}, minus the ownership check. Idempotent.
     *
     * @throws com.carsocialmedia.backend.forums.exception.ThreadNotFoundException if no such thread
     */
    void deleteThreadAsModerator(UUID threadId);

    /**
     * Deletes a reply on a moderator's behalf: same soft-with-children / hard-without semantics as
     * {@link #deletePost}, minus the ownership check. Idempotent.
     *
     * @throws com.carsocialmedia.backend.forums.exception.ForumPostNotFoundException if no such reply
     */
    void deleteReplyAsModerator(UUID postId);
}
