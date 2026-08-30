package com.tweakdapp.backend.forums;

import com.tweakdapp.backend.forums.dto.CursorPage;
import com.tweakdapp.backend.forums.dto.ForumSuggestionDto;
import com.tweakdapp.backend.forums.dto.ReplyDto;
import com.tweakdapp.backend.forums.dto.ShortcutDto;
import com.tweakdapp.backend.forums.dto.ThreadCardDto;
import com.tweakdapp.backend.forums.dto.ThreadDetailDto;
import com.tweakdapp.backend.forums.dto.TopicDto;
import com.tweakdapp.backend.forums.dto.request.CreateReplyRequest;
import com.tweakdapp.backend.forums.dto.request.CreateShortcutRequest;
import com.tweakdapp.backend.forums.dto.request.CreateThreadRequest;
import com.tweakdapp.backend.forums.dto.request.ReorderShortcutsRequest;
import com.tweakdapp.backend.forums.dto.request.UpdateReplyRequest;
import com.tweakdapp.backend.forums.dto.request.UpdateShortcutRequest;
import com.tweakdapp.backend.forums.dto.request.UpdateThreadRequest;
import com.tweakdapp.backend.report.dto.ReportReasonDto;

import java.time.Instant;
import java.util.Collection;
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

    // ---- tags (the forums half of a profile's "tags" section) ---------------
    //
    // The tags module merges these keyset streams with the posts module's into one chronological
    // page, then asks for the DTOs of just the ids that made the page. Content the tagged user
    // wrote themselves is filtered out here, in SQL.

    /**
     * One keyset page of refs to the threads the given user — or one of their cars — is tagged in,
     * most recently tagged first, excluding threads they started. Anonymized ("deleted") threads
     * stay in: they keep their title, body and replies.
     *
     * @param userId the tagged user
     * @param ownedCarIds that user's car ids, resolved once by the caller and reused across all
     *        four tag streams; empty means "match person tags only"
     * @param cursorTaggedAt the previous page's last {@code taggedAt}, or {@code null} for the first page
     * @param cursorId the previous page's last content id, or {@code null} for the first page
     * @param limit max refs to return (the caller over-fetches by one to detect a further page)
     */
    List<com.tweakdapp.backend.shared.tagging.TaggedContentRef> findTaggedThreadRefs(
            UUID userId, Collection<UUID> ownedCarIds, Instant cursorTaggedAt, UUID cursorId, int limit);

    /**
     * One keyset page of refs to the thread replies the given user — or one of their cars — is
     * tagged in, most recently tagged first. Excludes their own replies and soft-deleted ones; the
     * ref's {@code parentId} is the reply's thread.
     *
     * @see #findTaggedThreadRefs for the parameters
     */
    List<com.tweakdapp.backend.shared.tagging.TaggedContentRef> findTaggedReplyRefs(
            UUID userId, Collection<UUID> ownedCarIds, Instant cursorTaggedAt, UUID cursorId, int limit);

    /**
     * Batch-assembles the given threads as list cards, in the requested order. Ids that no longer
     * resolve are dropped rather than erroring — a thread deleted mid-request just disappears from
     * the caller's page.
     */
    List<ThreadCardDto> getThreadCardsByIds(UUID viewerId, List<UUID> threadIds);

    /**
     * Batch-assembles the given replies, in the requested order, resolving each one's thread author
     * so the "Author" badge stays correct across a mixed-thread batch. Ids that no longer resolve
     * are dropped.
     */
    List<ReplyDto> getRepliesByIds(UUID viewerId, List<UUID> replyIds);

    /**
     * Removes the caller's own tags from a thread: their person tag plus the tags of any of their
     * own cars on it (dropping only the person tag would leave a car whose owner is not tagged,
     * breaking the rule the tagging endpoints enforce). Idempotent.
     *
     * @throws com.tweakdapp.backend.forums.exception.ThreadNotFoundException if no such thread
     */
    void removeSelfTagsFromThread(UUID userId, UUID threadId);

    /**
     * Removes the caller's own person and car tags from a reply; see
     * {@link #removeSelfTagsFromThread}. Idempotent.
     *
     * @throws com.tweakdapp.backend.forums.exception.ForumPostNotFoundException if no such reply
     */
    void removeSelfTagsFromReply(UUID userId, UUID replyId);

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
    Optional<com.tweakdapp.backend.shared.moderation.ModerationContentDto> findThreadModerationSnapshot(UUID threadId);

    /**
     * The reported reply in the uniform moderation shape, or empty if the reply is gone (see
     * {@link #findThreadModerationSnapshot}).
     */
    Optional<com.tweakdapp.backend.shared.moderation.ModerationContentDto> findReplyModerationSnapshot(UUID postId);

    /**
     * Deletes a thread on a moderator's behalf: same anonymize-with-replies / hard-delete-without
     * semantics as {@link #deleteThread}, minus the ownership check. Idempotent.
     *
     * @throws com.tweakdapp.backend.forums.exception.ThreadNotFoundException if no such thread
     */
    void deleteThreadAsModerator(UUID threadId);

    /**
     * Deletes a reply on a moderator's behalf: same soft-with-children / hard-without semantics as
     * {@link #deletePost}, minus the ownership check. Idempotent.
     *
     * @throws com.tweakdapp.backend.forums.exception.ForumPostNotFoundException if no such reply
     */
    void deleteReplyAsModerator(UUID postId);
}
