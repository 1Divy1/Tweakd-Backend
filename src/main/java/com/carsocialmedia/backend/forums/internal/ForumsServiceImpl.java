package com.carsocialmedia.backend.forums.internal;

import com.carsocialmedia.backend.forums.ForumsService;
import com.carsocialmedia.backend.forums.dto.CursorPage;
import com.carsocialmedia.backend.forums.dto.ForumSuggestionDto;
import com.carsocialmedia.backend.forums.dto.ReplyDto;
import com.carsocialmedia.backend.forums.dto.ShortcutDto;
import com.carsocialmedia.backend.forums.dto.ThreadCardDto;
import com.carsocialmedia.backend.forums.dto.ThreadDetailDto;
import com.carsocialmedia.backend.forums.dto.TopicDto;
import com.carsocialmedia.backend.forums.dto.TopicGroupDto;
import com.carsocialmedia.backend.forums.dto.request.CreateReplyRequest;
import com.carsocialmedia.backend.forums.dto.request.CreateShortcutRequest;
import com.carsocialmedia.backend.forums.dto.request.CreateThreadRequest;
import com.carsocialmedia.backend.forums.dto.request.ReorderShortcutsRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateReplyRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateShortcutRequest;
import com.carsocialmedia.backend.forums.dto.request.UpdateThreadRequest;
import com.carsocialmedia.backend.forums.exception.CannotReportOwnForumContentException;
import com.carsocialmedia.backend.forums.exception.ForumPostDeletedException;
import com.carsocialmedia.backend.forums.exception.ForumPostNotFoundException;
import com.carsocialmedia.backend.forums.exception.InvalidReferenceException;
import com.carsocialmedia.backend.forums.exception.InvalidShortcutException;
import com.carsocialmedia.backend.forums.exception.NotContentOwnerException;
import com.carsocialmedia.backend.forums.exception.ShortcutNotFoundException;
import com.carsocialmedia.backend.forums.exception.ThreadDeletedException;
import com.carsocialmedia.backend.forums.exception.ThreadLockedException;
import com.carsocialmedia.backend.forums.exception.ThreadNotFoundException;
import com.carsocialmedia.backend.forums.internal.entities.ForumPostEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumShortcutEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadSaveEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTopicEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTopicId;
import com.carsocialmedia.backend.forums.internal.entities.ForumTopicEntity;
import com.carsocialmedia.backend.forums.internal.repositories.ForumPostLikeRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumPostRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumShortcutRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadLikeRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadReadRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadSaveRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadTopicRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumTopicRepository;
import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.CarBrandDto;
import com.carsocialmedia.backend.garage.dto.CarModelDto;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.carsocialmedia.backend.report.ReportService;
import com.carsocialmedia.backend.report.dto.ReportReasonDto;
import com.carsocialmedia.backend.shared.moderation.ModerationContentDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ForumsServiceImpl implements ForumsService {

    /** Hard cap on page size so a client can't request an unbounded page. */
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_SUGGESTIONS = 10;
    private static final int MAX_SUGGESTIONS = 50;

    private final ForumTopicRepository topicRepository;
    private final ForumThreadRepository threadRepository;
    private final ForumPostRepository postRepository;
    private final ForumThreadTopicRepository threadTopicRepository;
    private final ForumThreadLikeRepository threadLikeRepository;
    private final ForumPostLikeRepository postLikeRepository;
    private final ForumThreadSaveRepository threadSaveRepository;
    private final ForumThreadReadRepository threadReadRepository;
    private final ForumShortcutRepository shortcutRepository;
    private final ProfileService profileService;
    private final GarageService garageService;
    private final ReportService reportService;

    @PersistenceContext
    private EntityManager entityManager;

    public ForumsServiceImpl(ForumTopicRepository topicRepository,
                             ForumThreadRepository threadRepository,
                             ForumPostRepository postRepository,
                             ForumThreadTopicRepository threadTopicRepository,
                             ForumThreadLikeRepository threadLikeRepository,
                             ForumPostLikeRepository postLikeRepository,
                             ForumThreadSaveRepository threadSaveRepository,
                             ForumThreadReadRepository threadReadRepository,
                             ForumShortcutRepository shortcutRepository,
                             ProfileService profileService,
                             GarageService garageService,
                             ReportService reportService) {
        this.topicRepository = topicRepository;
        this.threadRepository = threadRepository;
        this.postRepository = postRepository;
        this.threadTopicRepository = threadTopicRepository;
        this.threadLikeRepository = threadLikeRepository;
        this.postLikeRepository = postLikeRepository;
        this.threadSaveRepository = threadSaveRepository;
        this.threadReadRepository = threadReadRepository;
        this.shortcutRepository = shortcutRepository;
        this.profileService = profileService;
        this.garageService = garageService;
        this.reportService = reportService;
    }

    // -------------------------------------------------------------------
    // TOPICS
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<TopicGroupDto> listTopics() {
        // Rows arrive ordered by kind then sort_order, so a LinkedHashMap preserves both the group
        // order and the intra-group order without re-sorting.
        Map<String, List<TopicDto>> byKind = new LinkedHashMap<>();
        for (ForumTopicEntity topic : topicRepository.findByActiveTrueOrderByKindAscSortOrderAsc()) {
            byKind.computeIfAbsent(topic.getKind(), k -> new ArrayList<>()).add(toTopicDto(topic));
        }
        return byKind.entrySet().stream()
                .map(e -> new TopicGroupDto(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ForumSuggestionDto> getSuggestions(int limit) {
        int n = limit <= 0 ? DEFAULT_SUGGESTIONS : Math.min(limit, MAX_SUGGESTIONS);

        // Pull the top candidates from each dimension (thread_count > 0), then merge and re-rank so
        // the final list is the most active hubs overall, whatever their kind. No bespoke scoring —
        // it reuses the denormalized thread counts.
        List<CarBrandDto> brands = garageService.findTopBrandsByThreadCount(n);
        List<CarModelDto> models = garageService.findTopModelsByThreadCount(n);
        List<ForumTopicEntity> topics = topicRepository
                .findByActiveTrueAndThreadCountGreaterThanOrderByThreadCountDesc(0, PageRequest.of(0, n));

        // Resolve owning-brand names so a model reads as "BMW M4" (brand name = the model's subtitle).
        Set<UUID> brandIds = models.stream().map(CarModelDto::brandId).collect(Collectors.toSet());
        Map<UUID, String> brandNames = brandIds.isEmpty() ? Map.of()
                : garageService.findBrandsByIds(brandIds).stream()
                        .collect(Collectors.toMap(CarBrandDto::id, CarBrandDto::name));

        List<ForumSuggestionDto> merged = new ArrayList<>(brands.size() + models.size() + topics.size());
        for (CarBrandDto b : brands) {
            merged.add(new ForumSuggestionDto("brand", b.id().toString(), b.name(), null, b.threadCount()));
        }
        for (CarModelDto m : models) {
            merged.add(new ForumSuggestionDto("model", m.id().toString(), m.model(),
                    brandNames.get(m.brandId()), m.threadCount()));
        }
        for (ForumTopicEntity t : topics) {
            merged.add(new ForumSuggestionDto("topic", t.getId(), t.getName(), null, t.getThreadCount()));
        }

        merged.sort(Comparator.comparingInt(ForumSuggestionDto::threadCount).reversed());
        return merged.stream().limit(n).toList();
    }

    // -------------------------------------------------------------------
    // READ — feed & hubs
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ThreadCardDto> getFeed(String currentUserId, String sort, String cursor, int size) {
        return listThreads(UUID.fromString(currentUserId), null, null, null, sort, cursor, size);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ThreadCardDto> getBrandThreads(String currentUserId, UUID brandId, String sort, String cursor, int size) {
        return listThreads(UUID.fromString(currentUserId), brandId, null, null, sort, cursor, size);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ThreadCardDto> getModelThreads(String currentUserId, UUID modelId, String sort, String topicId, String cursor, int size) {
        return listThreads(UUID.fromString(currentUserId), null, modelId, blankToNull(topicId), sort, cursor, size);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ThreadCardDto> getTopicThreads(String currentUserId, String topicId, String sort, UUID brandId, UUID modelId, String cursor, int size) {
        return listThreads(UUID.fromString(currentUserId), brandId, modelId, topicId, sort, cursor, size);
    }

    /**
     * The one keyset engine behind every thread list. The lens filters ({@code brandId} /
     * {@code modelId} / {@code topicId}, any of them null) and the active {@link ForumSort} pick the
     * repository query and the cursor key; assembly into cards is shared.
     */
    private CursorPage<ThreadCardDto> listThreads(UUID viewerId, UUID brandId, UUID modelId, String topicId,
                                                  String sortRaw, String cursor, int size) {
        int limit = clampSize(size);
        ForumSort sort = ForumSort.from(sortRaw);
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<ForumThreadEntity> rows = switch (sort) {
            case HOT -> {
                RankCursor from = RankCursor.decode(cursor);
                yield threadRepository.findHotPage(brandId, modelId, topicId,
                        from == null,
                        from == null ? null : from.rankingScore(),
                        from == null ? null : from.id(),
                        pageable);
            }
            case NEW -> {
                TimeCursor from = TimeCursor.decode(cursor);
                yield threadRepository.findNewPage(brandId, modelId, topicId,
                        from == null,
                        from == null ? null : from.timestamp(),
                        from == null ? null : from.id(),
                        pageable);
            }
            case ACTIVE -> {
                TimeCursor from = TimeCursor.decode(cursor);
                yield threadRepository.findActivePage(brandId, modelId, topicId,
                        from == null,
                        from == null ? null : from.timestamp(),
                        from == null ? null : from.id(),
                        pageable);
            }
        };

        boolean hasMore = rows.size() > limit;
        List<ForumThreadEntity> page = hasMore ? rows.subList(0, limit) : rows;

        List<ThreadCardDto> items = toThreadCards(page, viewerId);
        String nextCursor = hasMore ? nextCursor(sort, page.getLast()) : null;
        return new CursorPage<>(items, nextCursor);
    }

    // -------------------------------------------------------------------
    // READ — thread detail & replies
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public ThreadDetailDto getThread(String currentUserId, UUID threadId) {
        UUID viewerId = UUID.fromString(currentUserId);
        ForumThreadEntity thread = loadThread(threadId);

        // Opening a thread marks it "seen" for this viewer (drives shortcut unread badges).
        threadReadRepository.insertIgnoringConflict(viewerId, threadId);

        ThreadCardDto card = toThreadCards(List.of(thread), viewerId).getFirst();
        boolean viewerHasLiked = !threadRepository.findLikedThreadIds(viewerId, List.of(threadId)).isEmpty();

        return new ThreadDetailDto(
                card.id(), card.title(), thread.getContent(),
                card.author(), card.brand(), card.model(), card.topics(),
                card.likesCount(), card.replyCount(),
                thread.getCreatedAt(), card.lastActivityAt(),
                card.pinned(), card.locked(), card.deleted(), viewerHasLiked, card.viewerHasSaved());
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ReplyDto> getReplies(String currentUserId, UUID threadId, String sortRaw, String cursor, int size) {
        UUID viewerId = UUID.fromString(currentUserId);
        ForumThreadEntity thread = loadThread(threadId); // 404 if the thread is missing
        UUID threadAuthorId = thread.getUserId();

        ReplySort sort = ReplySort.from(sortRaw);
        int limit = clampSize(size);
        TimeCursor from = TimeCursor.decode(cursor);
        Pageable pageable = PageRequest.of(0, limit + 1);

        // One keyset page of top-level replies, no subtrees — the client expands a reply's children
        // on demand via getPostReplies.
        List<ForumPostEntity> roots = switch (sort) {
            case OLD -> postRepository.findRootReplyPage(threadId, from == null,
                    from == null ? null : from.timestamp(), from == null ? null : from.id(), pageable);
            case NEW -> postRepository.findRootReplyPageDesc(threadId, from == null,
                    from == null ? null : from.timestamp(), from == null ? null : from.id(), pageable);
        };

        return toReplyPage(roots, limit, viewerId, threadAuthorId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ReplyDto> getPostReplies(String currentUserId, UUID postId, String sortRaw, String cursor, int size) {
        UUID viewerId = UUID.fromString(currentUserId);
        ForumPostEntity parent = postRepository.findById(postId)
                .orElseThrow(() -> new ForumPostNotFoundException(postId));
        UUID threadAuthorId = loadThread(parent.getThreadId()).getUserId();

        ReplySort sort = ReplySort.from(sortRaw);
        int limit = clampSize(size);
        TimeCursor from = TimeCursor.decode(cursor);
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<ForumPostEntity> children = switch (sort) {
            case OLD -> postRepository.findChildReplyPage(postId, from == null,
                    from == null ? null : from.timestamp(), from == null ? null : from.id(), pageable);
            case NEW -> postRepository.findChildReplyPageDesc(postId, from == null,
                    from == null ? null : from.timestamp(), from == null ? null : from.id(), pageable);
        };

        return toReplyPage(children, limit, viewerId, threadAuthorId);
    }

    // -------------------------------------------------------------------
    // WRITE — threads & replies
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public ThreadDetailDto createThread(String currentUserId, CreateThreadRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        List<String> topicIds = distinct(request.topicIds());
        validateTopicsActive(topicIds);

        UUID threadId = UUID.randomUUID();
        ForumThreadEntity thread = new ForumThreadEntity();
        thread.setId(threadId);
        thread.setUserId(userId);
        thread.setTitle(request.title().strip());
        thread.setContent(blankToNull(request.content()));

        // Car scoping: a model wins (the trigger derives brand from it, so leave brandId null);
        // otherwise a bare brand for a brand-level thread; otherwise a general thread.
        if (request.modelId() != null) {
            validateModelExists(request.modelId());
            thread.setModelId(request.modelId());
        } else if (request.brandId() != null) {
            validateBrandExists(request.brandId());
            thread.setBrandId(request.brandId());
        }

        thread.setDeleted(false);
        thread.setUpdatedAt(Instant.now());
        threadRepository.save(thread);

        for (String topicId : topicIds) {
            ForumThreadTopicEntity tag = new ForumThreadTopicEntity();
            tag.setId(new ForumThreadTopicId(threadId, topicId));
            threadTopicRepository.save(tag);
        }

        // Flush the INSERTs and clear the context so the re-fetch issues a real SELECT and picks up
        // the trigger-set columns (derived brand_id, ranking_score, created_at, last_activity_at).
        entityManager.flush();
        entityManager.clear();

        return getThread(currentUserId, threadId);
    }

    @Override
    @Transactional
    public ThreadDetailDto updateThread(String currentUserId, UUID threadId, UpdateThreadRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        ForumThreadEntity thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ThreadNotFoundException(threadId));
        if (!thread.getUserId().equals(userId)) {
            throw new NotContentOwnerException();
        }
        if (thread.isDeleted()) {
            throw new ThreadDeletedException();
        }
        if (thread.isLocked()) {
            throw new ThreadLockedException();
        }

        thread.setContent(blankToNull(request.content()));
        thread.setUpdatedAt(Instant.now());
        threadRepository.save(thread);

        // The BEFORE UPDATE trigger recomputes ranking_score; flush + clear so the re-read below
        // returns the fresh row instead of the stale first-level-cache copy.
        entityManager.flush();
        entityManager.clear();

        return getThread(currentUserId, threadId);
    }

    @Override
    @Transactional
    public ReplyDto addReply(String currentUserId, UUID threadId, CreateReplyRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        ForumThreadEntity thread = loadThread(threadId);
        if (thread.isLocked()) {
            throw new ThreadLockedException();
        }

        UUID parentId = request.parentPostId();
        if (parentId != null) {
            ForumPostEntity parent = postRepository.findById(parentId)
                    .orElseThrow(() -> new ForumPostNotFoundException(parentId));
            if (!parent.getThreadId().equals(threadId)) {
                throw new ForumPostNotFoundException(parentId);
            }
            if (parent.isDeleted()) {
                throw new ForumPostDeletedException(parentId);
            }
        }

        UUID postId = UUID.randomUUID();
        ForumPostEntity post = new ForumPostEntity();
        post.setId(postId);
        post.setThreadId(threadId);
        post.setUserId(userId);
        post.setParentPostId(parentId);
        post.setContent(request.content().strip());
        post.setDeleted(false);
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);

        entityManager.flush();
        entityManager.clear();

        ForumPostEntity hydrated = postRepository.findById(postId)
                .orElseThrow(() -> new ForumPostNotFoundException(postId));
        return toReplyDtos(List.of(hydrated), userId, thread.getUserId()).getFirst();
    }

    @Override
    @Transactional
    public ReplyDto updateReply(String currentUserId, UUID postId, UpdateReplyRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        ForumPostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ForumPostNotFoundException(postId));
        if (!post.getUserId().equals(userId)) {
            throw new NotContentOwnerException();
        }
        if (post.isDeleted()) {
            throw new ForumPostDeletedException(postId);
        }
        ForumThreadEntity thread = loadThread(post.getThreadId());
        if (thread.isLocked()) {
            throw new ThreadLockedException();
        }

        post.setContent(request.content().strip());
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);

        return toReplyDtos(List.of(post), userId, thread.getUserId()).getFirst();
    }

    // -------------------------------------------------------------------
    // WRITE — likes (idempotent; counts maintained by triggers)
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public void likeThread(String currentUserId, UUID threadId) {
        UUID userId = UUID.fromString(currentUserId);
        loadThread(threadId);
        // ON CONFLICT DO NOTHING: idempotent and race-safe against concurrent double-taps.
        threadLikeRepository.insertIgnoringConflict(threadId, userId);
    }

    @Override
    @Transactional
    public void unlikeThread(String currentUserId, UUID threadId) {
        UUID userId = UUID.fromString(currentUserId);
        threadLikeRepository.deleteByIdThreadIdAndIdUserId(threadId, userId);
    }

    @Override
    @Transactional
    public void likePost(String currentUserId, UUID postId) {
        UUID userId = UUID.fromString(currentUserId);
        ForumPostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ForumPostNotFoundException(postId));
        if (post.isDeleted()) {
            throw new ForumPostDeletedException(postId);
        }
        // ON CONFLICT DO NOTHING: idempotent and race-safe against concurrent double-taps.
        postLikeRepository.insertIgnoringConflict(postId, userId);
    }

    @Override
    @Transactional
    public void unlikePost(String currentUserId, UUID postId) {
        UUID userId = UUID.fromString(currentUserId);
        postLikeRepository.deleteByIdPostIdAndIdUserId(postId, userId);
    }

    // -------------------------------------------------------------------
    // SAVES (bookmarks, owner-scoped; idempotent)
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public void saveThread(String currentUserId, UUID threadId) {
        UUID userId = UUID.fromString(currentUserId);
        loadThread(threadId); // 404 if the thread is missing
        // ON CONFLICT DO NOTHING: idempotent and race-safe against concurrent double-taps.
        threadSaveRepository.insertIgnoringConflict(threadId, userId);
    }

    @Override
    @Transactional
    public void unsaveThread(String currentUserId, UUID threadId) {
        UUID userId = UUID.fromString(currentUserId);
        threadSaveRepository.deleteByIdThreadIdAndIdUserId(threadId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ThreadCardDto> getSavedThreads(String currentUserId, String cursor, int size) {
        UUID viewerId = UUID.fromString(currentUserId);
        int limit = clampSize(size);
        TimeCursor from = TimeCursor.decode(cursor);

        List<ForumThreadSaveEntity> saves = threadSaveRepository.findSavedPage(
                viewerId,
                from == null,
                from == null ? null : from.timestamp(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        boolean hasMore = saves.size() > limit;
        List<ForumThreadSaveEntity> page = hasMore ? saves.subList(0, limit) : saves;

        // Load the threads and re-order them to match the (newest-save-first) order, since
        // findAllById does not preserve order.
        List<UUID> orderedIds = page.stream().map(s -> s.getId().getThreadId()).toList();
        Map<UUID, ForumThreadEntity> byId = threadRepository.findAllById(orderedIds).stream()
                .collect(Collectors.toMap(ForumThreadEntity::getId, Function.identity()));
        List<ForumThreadEntity> threads = orderedIds.stream()
                .map(byId::get).filter(Objects::nonNull).toList();

        List<ThreadCardDto> items = toThreadCards(threads, viewerId);
        String nextCursor = hasMore
                ? new TimeCursor(page.getLast().getCreatedAt(), page.getLast().getId().getThreadId()).encode()
                : null;
        return new CursorPage<>(items, nextCursor);
    }

    // -------------------------------------------------------------------
    // WRITE — delete (author-only)
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public void deleteThread(String currentUserId, UUID threadId) {
        UUID userId = UUID.fromString(currentUserId);
        ForumThreadEntity thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ThreadNotFoundException(threadId));
        if (!thread.getUserId().equals(userId)) {
            throw new NotContentOwnerException();
        }
        removeThread(thread);
    }

    @Override
    @Transactional
    public void deletePost(String currentUserId, UUID postId) {
        UUID userId = UUID.fromString(currentUserId);
        ForumPostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ForumPostNotFoundException(postId));
        if (!post.getUserId().equals(userId)) {
            throw new NotContentOwnerException();
        }
        removeReply(post);
    }

    /**
     * The delete semantics shared by the author and moderator paths. With replies, only anonymize:
     * the thread stays fully visible and repliable, but the author is hidden from every DTO. With
     * no replies, remove it outright (the DB cascades topics / likes).
     */
    private void removeThread(ForumThreadEntity thread) {
        if (thread.isDeleted()) {
            return;
        }
        if (thread.getReplyCount() > 0) {
            thread.setDeleted(true);
            thread.setUpdatedAt(Instant.now());
            threadRepository.save(thread);
        } else {
            threadRepository.delete(thread);
        }
    }

    /**
     * The delete semantics shared by the author and moderator paths. Direct children present →
     * soft delete so descendants survive; otherwise hard delete.
     */
    private void removeReply(ForumPostEntity post) {
        if (post.isDeleted()) {
            return;
        }
        if (post.getReplyCount() > 0) {
            post.setDeleted(true);
            post.setUpdatedAt(Instant.now());
            postRepository.save(post);
        } else {
            UUID parentId = post.getParentPostId();
            postRepository.delete(post);
            collapseDeletedAncestors(parentId);
        }
    }

    // -------------------------------------------------------------------
    // MODERATION — called by the admin module (which does the auth)
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public ModerationContentDto getThreadModerationSnapshot(UUID threadId) {
        ForumThreadEntity thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ThreadNotFoundException(threadId));
        String body = thread.getContent();
        String content = body == null || body.isBlank()
                ? thread.getTitle()
                : thread.getTitle() + "\n\n" + body;
        return new ModerationContentDto(
                thread.getId(), thread.getUserId(), content, List.of(), thread.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public ModerationContentDto getReplyModerationSnapshot(UUID postId) {
        ForumPostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ForumPostNotFoundException(postId));
        return new ModerationContentDto(
                post.getId(), post.getUserId(), post.getContent(), List.of(), post.getCreatedAt());
    }

    @Override
    @Transactional
    public void deleteThreadAsModerator(UUID threadId) {
        ForumThreadEntity thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new ThreadNotFoundException(threadId));
        removeThread(thread);
    }

    @Override
    @Transactional
    public void deleteReplyAsModerator(UUID postId) {
        ForumPostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ForumPostNotFoundException(postId));
        removeReply(post);
    }

    /**
     * Walks up from a just-hard-deleted reply and removes any ancestor that is <em>itself already
     * soft-deleted</em> and, after the removal, has no remaining children. This keeps the tree clean:
     * a soft-deleted reply only lingers as a "[deleted]" placeholder for as long as it actually
     * anchors visible children — the moment its last child goes, the placeholder (and its
     * contribution to the counts) goes with it. A live (non-deleted) ancestor is always kept, even
     * if it becomes childless.
     */
    private void collapseDeletedAncestors(UUID parentId) {
        while (parentId != null) {
            ForumPostEntity parent = postRepository.findById(parentId).orElse(null);
            if (parent == null || !parent.isDeleted()) {
                break;
            }
            // The pending delete is flushed by this derived-query lookup, so it reflects reality.
            if (postRepository.existsByParentPostId(parentId)) {
                break;
            }
            UUID grandParentId = parent.getParentPostId();
            postRepository.delete(parent);
            parentId = grandParentId;
        }
    }

    // -------------------------------------------------------------------
    // SHORTCUTS (owner-scoped)
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ShortcutDto> listShortcuts(String currentUserId) {
        UUID userId = UUID.fromString(currentUserId);
        return toShortcutDtos(shortcutRepository.findByUserIdOrderBySortOrderAsc(userId));
    }

    @Override
    @Transactional
    public ShortcutDto createShortcut(String currentUserId, CreateShortcutRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        if (request.brandId() == null && request.modelId() == null && blankToNull(request.topicId()) == null) {
            throw new InvalidShortcutException("A shortcut must set at least one of brand, model, or topic");
        }
        if (request.brandId() != null) {
            validateBrandExists(request.brandId());
        }
        if (request.modelId() != null) {
            validateModelExists(request.modelId());
        }
        String topicId = blankToNull(request.topicId());
        if (topicId != null && !topicRepository.existsById(topicId)) {
            throw new InvalidReferenceException("Unknown topic: " + topicId);
        }

        List<ForumShortcutEntity> existing = shortcutRepository.findByUserIdOrderBySortOrderAsc(userId);
        int nextSortOrder = existing.isEmpty() ? 0 : existing.getLast().getSortOrder() + 1;

        UUID id = UUID.randomUUID();
        ForumShortcutEntity shortcut = new ForumShortcutEntity();
        shortcut.setId(id);
        shortcut.setUserId(userId);
        shortcut.setName(request.name().strip());
        shortcut.setBrandId(request.brandId());
        shortcut.setModelId(request.modelId());
        shortcut.setTopicId(topicId);
        shortcut.setSortOrder(nextSortOrder);
        shortcut.setNotify(request.notifyEnabled() != null && request.notifyEnabled());
        shortcutRepository.save(shortcut);

        entityManager.flush();
        entityManager.clear();

        ForumShortcutEntity hydrated = shortcutRepository.findById(id).orElseThrow();
        return toShortcutDtos(List.of(hydrated)).getFirst();
    }

    @Override
    @Transactional
    public ShortcutDto updateShortcut(String currentUserId, UUID shortcutId, UpdateShortcutRequest request) {
        UUID userId = UUID.fromString(currentUserId);
        ForumShortcutEntity shortcut = shortcutRepository.findByIdAndUserId(shortcutId, userId)
                .orElseThrow(() -> new ShortcutNotFoundException(shortcutId));

        if (request.name() != null) {
            String name = request.name().strip();
            if (name.isEmpty()) {
                throw new InvalidShortcutException("Shortcut name must not be blank");
            }
            shortcut.setName(name);
        }
        if (request.notifyEnabled() != null) {
            shortcut.setNotify(request.notifyEnabled());
        }
        shortcutRepository.save(shortcut);
        return toShortcutDtos(List.of(shortcut)).getFirst();
    }

    @Override
    @Transactional
    public void deleteShortcut(String currentUserId, UUID shortcutId) {
        UUID userId = UUID.fromString(currentUserId);
        ForumShortcutEntity shortcut = shortcutRepository.findByIdAndUserId(shortcutId, userId)
                .orElseThrow(() -> new ShortcutNotFoundException(shortcutId));
        shortcutRepository.delete(shortcut);
    }

    @Override
    @Transactional
    public List<ShortcutDto> reorderShortcuts(String currentUserId, ReorderShortcutsRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        Map<UUID, ForumShortcutEntity> owned = shortcutRepository.findByUserIdOrderBySortOrderAsc(userId).stream()
                .collect(Collectors.toMap(ForumShortcutEntity::getId, Function.identity()));

        List<UUID> orderedIds = request.orderedIds();
        // The request must be exactly the user's set — no duplicates, no foreign ids, no omissions —
        // so the resulting sort_order values are a clean 0..n-1 permutation.
        Set<UUID> requested = new HashSet<>(orderedIds);
        if (requested.size() != orderedIds.size() || !requested.equals(owned.keySet())) {
            throw new InvalidShortcutException("orderedIds must list exactly your shortcuts, once each");
        }

        for (int i = 0; i < orderedIds.size(); i++) {
            owned.get(orderedIds.get(i)).setSortOrder(i);
        }
        List<ForumShortcutEntity> reordered = orderedIds.stream().map(owned::get).toList();
        shortcutRepository.saveAll(reordered);
        return toShortcutDtos(reordered);
    }

    // -------------------------------------------------------------------
    // REPORTING (this module validates the target + blocks self-reports;
    // the report module owns reason/duplicate validation and persistence)
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public void reportThread(String currentUserId, UUID threadId, UUID reasonId) {
        UUID reporterId = UUID.fromString(currentUserId);
        ForumThreadEntity thread = loadThread(threadId);
        if (thread.getUserId().equals(reporterId)) {
            throw new CannotReportOwnForumContentException();
        }
        reportService.reportForumThread(reporterId, threadId, reasonId);
    }

    @Override
    @Transactional
    public void reportReply(String currentUserId, UUID postId, UUID reasonId) {
        UUID reporterId = UUID.fromString(currentUserId);
        ForumPostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ForumPostNotFoundException(postId));
        if (post.getUserId().equals(reporterId)) {
            throw new CannotReportOwnForumContentException();
        }
        reportService.reportForumReply(reporterId, postId, reasonId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportReasonDto> listThreadReportReasons() {
        return reportService.listForumThreadReportReasons();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReportReasonDto> listReplyReportReasons() {
        return reportService.listForumReplyReportReasons();
    }

    // -------------------------------------------------------------------
    // ASSEMBLY HELPERS
    // -------------------------------------------------------------------

    /**
     * Assembles a page of thread cards with a fixed, small number of queries regardless of page size
     * (no N+1): one batch for the topic junction, one topic lookup, one profile lookup for authors,
     * and one brand + one model lookup through the garage module. Output preserves input order.
     *
     * <p>A deleted (anonymized) thread keeps all its content but gets a {@code null} author — its
     * author id is excluded from the profile lookup so the identity never even leaves the DB layer.
     */
    private List<ThreadCardDto> toThreadCards(List<ForumThreadEntity> threads, UUID viewerId) {
        if (threads.isEmpty()) {
            return List.of();
        }

        List<UUID> threadIds = threads.stream().map(ForumThreadEntity::getId).toList();

        Set<UUID> saved = Set.copyOf(threadSaveRepository.findSavedThreadIds(viewerId, threadIds));

        Map<UUID, List<String>> topicIdsByThread = threadTopicRepository.findByIdThreadIdIn(threadIds).stream()
                .collect(Collectors.groupingBy(tt -> tt.getId().getThreadId(),
                        Collectors.mapping(tt -> tt.getId().getTopicId(), Collectors.toList())));

        Set<String> allTopicIds = topicIdsByThread.values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        Map<String, TopicDto> topics = topicRepository.findAllById(allTopicIds).stream()
                .collect(Collectors.toMap(ForumTopicEntity::getId, this::toTopicDto));

        Set<UUID> authorIds = threads.stream()
                .filter(t -> !t.isDeleted())
                .map(ForumThreadEntity::getUserId).collect(Collectors.toSet());
        Map<UUID, ProfileSearchResultDto> authors = authorIds.isEmpty() ? Map.of()
                : profileService.findByIds(authorIds).stream()
                        .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));

        Set<UUID> brandIds = threads.stream().map(ForumThreadEntity::getBrandId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, CarBrandDto> brands = garageService.findBrandsByIds(brandIds).stream()
                .collect(Collectors.toMap(CarBrandDto::id, Function.identity()));

        Set<UUID> modelIds = threads.stream().map(ForumThreadEntity::getModelId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, CarModelDto> models = garageService.findModelsByIds(modelIds).stream()
                .collect(Collectors.toMap(CarModelDto::id, Function.identity()));

        return threads.stream()
                .map(t -> new ThreadCardDto(
                        t.getId(),
                        t.getTitle(),
                        t.isDeleted() ? null : authors.get(t.getUserId()),
                        t.getBrandId() == null ? null : brands.get(t.getBrandId()),
                        t.getModelId() == null ? null : models.get(t.getModelId()),
                        topicIdsByThread.getOrDefault(t.getId(), List.of()).stream()
                                .map(topics::get)
                                .filter(Objects::nonNull)
                                .toList(),
                        t.getLikesCount(),
                        t.getReplyCount(),
                        t.getLastActivityAt(),
                        t.isPinned(),
                        t.isLocked(),
                        t.isDeleted(),
                        saved.contains(t.getId())))
                .toList();
    }

    /**
     * Turns a {@code size + 1} keyset load of replies (one level of the tree — thread roots or one
     * reply's children, oldest first) into a {@link CursorPage}, trimming the sentinel row and
     * encoding the next {@link TimeCursor} when there is a further page.
     */
    private CursorPage<ReplyDto> toReplyPage(List<ForumPostEntity> rows, int limit, UUID viewerId, UUID threadAuthorId) {
        boolean hasMore = rows.size() > limit;
        List<ForumPostEntity> page = hasMore ? rows.subList(0, limit) : rows;

        List<ReplyDto> items = toReplyDtos(page, viewerId, threadAuthorId);
        String nextCursor = hasMore
                ? new TimeCursor(page.getLast().getCreatedAt(), page.getLast().getId()).encode()
                : null;
        return new CursorPage<>(items, nextCursor);
    }

    /**
     * Assembles flat {@link ReplyDto}s (no nesting — children are fetched on demand) with one
     * profile batch and one like-flag batch for the whole page. A deleted reply is a "[deleted]"
     * placeholder: author and content are nulled, and its author id is excluded from the lookup. A
     * reply whose author is {@code threadAuthorId} (the OP) is flagged {@code isAuthor} for the
     * "Author" badge.
     */
    private List<ReplyDto> toReplyDtos(List<ForumPostEntity> posts, UUID viewerId, UUID threadAuthorId) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<UUID> postIds = posts.stream().map(ForumPostEntity::getId).toList();
        Set<UUID> authorIds = posts.stream()
                .filter(p -> !p.isDeleted())
                .map(ForumPostEntity::getUserId).collect(Collectors.toSet());
        Map<UUID, ProfileSearchResultDto> authors = authorIds.isEmpty() ? Map.of()
                : profileService.findByIds(authorIds).stream()
                        .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));
        Set<UUID> liked = Set.copyOf(postRepository.findLikedPostIds(viewerId, postIds));

        return posts.stream()
                .map(p -> new ReplyDto(
                        p.getId(),
                        p.isDeleted() ? null : authors.get(p.getUserId()),
                        p.isDeleted() ? null : p.getContent(),
                        p.getLikesCount(),
                        p.getReplyCount(),
                        p.isDeleted(),
                        !p.isDeleted() && p.getUserId().equals(threadAuthorId),
                        liked.contains(p.getId()),
                        p.getCreatedAt()))
                .toList();
    }

    private List<ShortcutDto> toShortcutDtos(List<ForumShortcutEntity> shortcuts) {
        if (shortcuts.isEmpty()) {
            return List.of();
        }
        Set<UUID> brandIds = shortcuts.stream().map(ForumShortcutEntity::getBrandId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, CarBrandDto> brands = garageService.findBrandsByIds(brandIds).stream()
                .collect(Collectors.toMap(CarBrandDto::id, Function.identity()));

        Set<UUID> modelIds = shortcuts.stream().map(ForumShortcutEntity::getModelId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, CarModelDto> models = garageService.findModelsByIds(modelIds).stream()
                .collect(Collectors.toMap(CarModelDto::id, Function.identity()));

        Set<String> topicIds = shortcuts.stream().map(ForumShortcutEntity::getTopicId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, TopicDto> topics = topicRepository.findAllById(topicIds).stream()
                .collect(Collectors.toMap(ForumTopicEntity::getId, this::toTopicDto));

        return shortcuts.stream()
                .map(s -> new ShortcutDto(
                        s.getId(),
                        s.getName(),
                        s.getBrandId() == null ? null : brands.get(s.getBrandId()),
                        s.getModelId() == null ? null : models.get(s.getModelId()),
                        s.getTopicId() == null ? null : topics.get(s.getTopicId()),
                        s.getSortOrder(),
                        s.isNotify(),
                        unreadCountFor(s),
                        s.getCreatedAt()))
                .toList();
    }

    /**
     * The unread-thread badge for a shortcut: threads matching its filter, created since the
     * shortcut was saved, that the user has not opened yet. One bounded count query per shortcut
     * (a user has only a handful). Clamped to {@code int} for the wire.
     */
    private int unreadCountFor(ForumShortcutEntity s) {
        long unread = threadRepository.countUnreadForShortcut(
                s.getUserId(), s.getBrandId(), s.getModelId(), s.getTopicId(), s.getCreatedAt());
        return (int) Math.min(unread, Integer.MAX_VALUE);
    }

    private TopicDto toTopicDto(ForumTopicEntity topic) {
        return new TopicDto(topic.getId(), topic.getName(), topic.getKind(), topic.getSortOrder(),
                topic.getColor(), topic.getThreadCount());
    }

    /**
     * Loads a thread by id or 404s. Deleted (anonymized) threads are loaded normally — deletion
     * only hides the author, the thread itself stays readable, likeable, and repliable.
     */
    private ForumThreadEntity loadThread(UUID threadId) {
        return threadRepository.findById(threadId)
                .orElseThrow(() -> new ThreadNotFoundException(threadId));
    }

    private void validateTopicsActive(List<String> topicIds) {
        if (topicIds.isEmpty()) {
            return;
        }
        List<ForumTopicEntity> found = topicRepository.findAllById(topicIds);
        if (found.size() != topicIds.size() || found.stream().anyMatch(t -> !t.isActive())) {
            throw new InvalidReferenceException("One or more topics do not exist or are inactive");
        }
    }

    private void validateBrandExists(UUID brandId) {
        if (garageService.findBrandsByIds(List.of(brandId)).isEmpty()) {
            throw new InvalidReferenceException("Unknown brand: " + brandId);
        }
    }

    private void validateModelExists(UUID modelId) {
        if (garageService.findModelsByIds(List.of(modelId)).isEmpty()) {
            throw new InvalidReferenceException("Unknown model: " + modelId);
        }
    }

    private String nextCursor(ForumSort sort, ForumThreadEntity last) {
        return switch (sort) {
            case HOT -> new RankCursor(last.getRankingScore(), last.getId()).encode();
            case NEW -> new TimeCursor(last.getCreatedAt(), last.getId()).encode();
            case ACTIVE -> new TimeCursor(last.getLastActivityAt(), last.getId()).encode();
        };
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    private static List<String> distinct(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).distinct().toList();
    }
}
