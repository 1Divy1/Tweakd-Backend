package com.carsocialmedia.backend.forums.internal;

import com.carsocialmedia.backend.forums.events.ForumReplyLikedEvent;
import com.carsocialmedia.backend.forums.events.ForumReplyRepliedEvent;
import com.carsocialmedia.backend.forums.events.ForumReplyTaggedEvent;
import com.carsocialmedia.backend.forums.events.ForumThreadLikedEvent;
import com.carsocialmedia.backend.forums.events.ForumThreadRepliedEvent;
import com.carsocialmedia.backend.forums.events.ForumThreadTaggedEvent;
import com.carsocialmedia.backend.forums.ForumsService;
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
import com.carsocialmedia.backend.forums.exception.CannotReportOwnForumContentException;
import com.carsocialmedia.backend.forums.exception.CarOwnerNotTaggedException;
import com.carsocialmedia.backend.forums.exception.ForumPostDeletedException;
import com.carsocialmedia.backend.forums.exception.ForumPostNotFoundException;
import com.carsocialmedia.backend.forums.exception.InvalidReferenceException;
import com.carsocialmedia.backend.forums.exception.InvalidShortcutException;
import com.carsocialmedia.backend.forums.exception.NotContentOwnerException;
import com.carsocialmedia.backend.forums.exception.ShortcutNotFoundException;
import com.carsocialmedia.backend.forums.exception.ThreadDeletedException;
import com.carsocialmedia.backend.forums.exception.ThreadLockedException;
import com.carsocialmedia.backend.forums.exception.ThreadNotFoundException;
import com.carsocialmedia.backend.forums.internal.entities.ForumReplyTaggedCarEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumReplyTaggedCarId;
import com.carsocialmedia.backend.forums.internal.entities.ForumReplyTaggedPersonEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumReplyTaggedPersonId;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadReplyEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumShortcutEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadSaveEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedCarEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedCarId;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedPersonEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTaggedPersonId;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTopicEntity;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTopicId;
import com.carsocialmedia.backend.forums.internal.entities.ForumThreadTopicOptionsEntity;
import com.carsocialmedia.backend.forums.internal.repositories.ForumPostLikeRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumPostRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumReplyTaggedCarRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumReplyTaggedPersonRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumShortcutRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadLikeRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadReadRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadSaveRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadTaggedCarRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadTaggedPersonRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadTopicRepository;
import com.carsocialmedia.backend.forums.internal.repositories.ForumThreadTopicOptionsRepository;
import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.CarBrandDto;
import com.carsocialmedia.backend.garage.dto.CarModelDto;
import com.carsocialmedia.backend.garage.dto.CarSummaryDto;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.carsocialmedia.backend.report.ReportService;
import com.carsocialmedia.backend.report.dto.ReportReasonDto;
import com.carsocialmedia.backend.shared.moderation.ModerationContentDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    private final ForumThreadTopicOptionsRepository topicRepository;
    private final ForumThreadRepository threadRepository;
    private final ForumPostRepository postRepository;
    private final ForumThreadTopicRepository threadTopicRepository;
    private final ForumThreadLikeRepository threadLikeRepository;
    private final ForumPostLikeRepository postLikeRepository;
    private final ForumThreadSaveRepository threadSaveRepository;
    private final ForumThreadReadRepository threadReadRepository;
    private final ForumThreadTaggedPersonRepository threadTaggedPersonRepository;
    private final ForumThreadTaggedCarRepository threadTaggedCarRepository;
    private final ForumReplyTaggedPersonRepository replyTaggedPersonRepository;
    private final ForumReplyTaggedCarRepository replyTaggedCarRepository;
    private final ForumShortcutRepository shortcutRepository;
    private final ProfileService profileService;
    private final GarageService garageService;
    private final ReportService reportService;
    private final ApplicationEventPublisher eventPublisher;

    /** Max length of the reply excerpt carried in a notification body. */
    private static final int EXCERPT_MAX_LENGTH = 80;

    @PersistenceContext
    private EntityManager entityManager;

    public ForumsServiceImpl(ForumThreadTopicOptionsRepository topicRepository,
                             ForumThreadRepository threadRepository,
                             ForumPostRepository postRepository,
                             ForumThreadTopicRepository threadTopicRepository,
                             ForumThreadLikeRepository threadLikeRepository,
                             ForumPostLikeRepository postLikeRepository,
                             ForumThreadSaveRepository threadSaveRepository,
                             ForumThreadReadRepository threadReadRepository,
                             ForumThreadTaggedPersonRepository threadTaggedPersonRepository,
                             ForumThreadTaggedCarRepository threadTaggedCarRepository,
                             ForumReplyTaggedPersonRepository replyTaggedPersonRepository,
                             ForumReplyTaggedCarRepository replyTaggedCarRepository,
                             ForumShortcutRepository shortcutRepository,
                             ProfileService profileService,
                             GarageService garageService,
                             ReportService reportService,
                             ApplicationEventPublisher eventPublisher) {
        this.topicRepository = topicRepository;
        this.threadRepository = threadRepository;
        this.postRepository = postRepository;
        this.threadTopicRepository = threadTopicRepository;
        this.threadLikeRepository = threadLikeRepository;
        this.postLikeRepository = postLikeRepository;
        this.threadSaveRepository = threadSaveRepository;
        this.threadReadRepository = threadReadRepository;
        this.threadTaggedPersonRepository = threadTaggedPersonRepository;
        this.threadTaggedCarRepository = threadTaggedCarRepository;
        this.replyTaggedPersonRepository = replyTaggedPersonRepository;
        this.replyTaggedCarRepository = replyTaggedCarRepository;
        this.shortcutRepository = shortcutRepository;
        this.profileService = profileService;
        this.garageService = garageService;
        this.reportService = reportService;
        this.eventPublisher = eventPublisher;
    }

    // -------------------------------------------------------------------
    // TOPICS
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<TopicDto> listTopics() {
        // A flat, curated-order list of the active topics — the client applies them as filters within
        // a brand or brand+model hub.
        return topicRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(this::toTopicDto)
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

        // Resolve owning-brand names so a model reads as "BMW M4" (brand name = the model's subtitle).
        // Extract brand IDs from the models, then fetch the names in one query (avoid N+1).
        Set<UUID> brandIds = models.stream().map(CarModelDto::brandId).collect(Collectors.toSet());
        Map<UUID, String> brandNames = brandIds.isEmpty() ? Map.of()
                : garageService.findBrandsByIds(brandIds).stream()
                        .collect(Collectors.toMap(CarBrandDto::id, CarBrandDto::name));

        List<ForumSuggestionDto> merged = new ArrayList<>(brands.size() + models.size());
        for (CarBrandDto b : brands) {
            merged.add(new ForumSuggestionDto("brand", b.id().toString(), b.name(), null, b.threadCount()));
        }
        for (CarModelDto m : models) {
            merged.add(new ForumSuggestionDto("model", m.id().toString(), m.model(),
                    brandNames.get(m.brandId()), m.threadCount()));
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
    public CursorPage<ThreadCardDto> getBrandThreads(String currentUserId, UUID brandId, String sort, String topicId, String cursor, int size) {
        return listThreads(UUID.fromString(currentUserId), brandId, null, blankToNull(topicId), sort, cursor, size);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPage<ThreadCardDto> getModelThreads(String currentUserId, UUID modelId, String sort, String topicId, String cursor, int size) {
        return listThreads(UUID.fromString(currentUserId), null, modelId, blankToNull(topicId), sort, cursor, size);
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
                card.taggedPeople(), card.taggedCars(),
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
        List<ForumThreadReplyEntity> roots = switch (sort) {
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
        ForumThreadReplyEntity parent = postRepository.findById(postId)
                .orElseThrow(() -> new ForumPostNotFoundException(postId));
        UUID threadAuthorId = loadThread(parent.getThreadId()).getUserId();

        ReplySort sort = ReplySort.from(sortRaw);
        int limit = clampSize(size);
        TimeCursor from = TimeCursor.decode(cursor);
        Pageable pageable = PageRequest.of(0, limit + 1);

        List<ForumThreadReplyEntity> children = switch (sort) {
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

        List<UUID> personIds = distinctIds(request.taggedPeople());
        List<UUID> carIds = distinctIds(request.taggedCars());
        validateTaggedPeopleExist(personIds);
        Map<UUID, UUID> ownerByCar = validateTaggedCars(userId, personIds, carIds);

        UUID threadId = UUID.randomUUID();
        ForumThreadEntity thread = new ForumThreadEntity();
        thread.setId(threadId);
        thread.setUserId(userId);
        thread.setTitle(request.title().strip());
        thread.setContent(request.content());

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

        insertThreadTags(threadId, personIds, carIds);

        // Flush the INSERTs and clear the context so the re-fetch issues a real SELECT and picks up
        // the trigger-set columns (derived brand_id, ranking_score, created_at, last_activity_at).
        entityManager.flush();
        entityManager.clear();

        // Everything tagged on create is new, so every tagged person (bar the author) is notified.
        publishTagEvents(personIds, carIds, ownerByCar, userId,
                recipient -> new ForumThreadTaggedEvent(threadId, recipient.userId(), userId, recipient.carTagged()));

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

        // PATCH semantics: a null list leaves that tag set alone, a non-null list replaces it. The
        // owner rule is validated against the RESULTING set, so dropping a person whose car stays
        // tagged is rejected.
        List<UUID> currentPersonIds = currentThreadTaggedPersonIds(threadId);
        List<UUID> currentCarIds = currentThreadTaggedCarIds(threadId);
        List<UUID> personIds = request.taggedPeople() != null ? distinctIds(request.taggedPeople()) : currentPersonIds;
        List<UUID> carIds = request.taggedCars() != null ? distinctIds(request.taggedCars()) : currentCarIds;
        if (request.taggedPeople() != null) {
            validateTaggedPeopleExist(personIds);
        }
        Map<UUID, UUID> ownerByCar = validateTaggedCars(thread.getUserId(), personIds, carIds);

        thread.setContent(blankToNull(request.content()));
        thread.setUpdatedAt(Instant.now());
        threadRepository.save(thread);

        if (request.taggedPeople() != null) {
            threadTaggedPersonRepository.deleteAllByIdThreadId(threadId);
        }
        if (request.taggedCars() != null) {
            threadTaggedCarRepository.deleteAllByIdThreadId(threadId);
        }
        entityManager.flush(); // the deletes must hit the DB before the re-inserts
        insertThreadTags(threadId,
                request.taggedPeople() != null ? personIds : List.of(),
                request.taggedCars() != null ? carIds : List.of());

        // The BEFORE UPDATE trigger recomputes ranking_score; flush + clear so the re-read below
        // returns the fresh row instead of the stale first-level-cache copy.
        entityManager.flush();
        entityManager.clear();

        // Only newly added tags notify — re-saving an unchanged tag set is silent.
        List<UUID> newPersonIds = added(currentPersonIds, personIds);
        List<UUID> newCarIds = added(currentCarIds, carIds);
        publishTagEvents(newPersonIds, newCarIds, ownerByCar, userId,
                recipient -> new ForumThreadTaggedEvent(threadId, recipient.userId(), userId, recipient.carTagged()));

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
        UUID parentAuthorId = null;
        if (parentId != null) {
            ForumThreadReplyEntity parent = postRepository.findById(parentId)
                    .orElseThrow(() -> new ForumPostNotFoundException(parentId));
            if (!parent.getThreadId().equals(threadId)) {
                throw new ForumPostNotFoundException(parentId);
            }
            if (parent.isDeleted()) {
                throw new ForumPostDeletedException(parentId);
            }
            parentAuthorId = parent.getUserId();
        }

        List<UUID> personIds = distinctIds(request.taggedPeople());
        List<UUID> carIds = distinctIds(request.taggedCars());
        validateTaggedPeopleExist(personIds);
        Map<UUID, UUID> ownerByCar = validateTaggedCars(userId, personIds, carIds);

        UUID postId = UUID.randomUUID();
        ForumThreadReplyEntity post = new ForumThreadReplyEntity();
        post.setId(postId);
        post.setThreadId(threadId);
        post.setUserId(userId);
        post.setParentPostId(parentId);
        post.setContent(request.content().strip());
        post.setDeleted(false);
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);

        insertReplyTags(postId, personIds, carIds);

        // TODO: BUG FIX - Exception thrown (line 435) - entityManager.flush();
        //  - Message: "org.postgresql.util.PSQLException: ERROR: relation "public.forum_posts" does not exist"
        //  - Reproduction: On an existing thread, when sending a reply to an existing reply,
        //                  the backend crashes the moment I click the send button.
        entityManager.flush();
        entityManager.clear();

        ForumThreadReplyEntity hydrated = postRepository.findById(postId)
                .orElseThrow(() -> new ForumPostNotFoundException(postId));

        String excerpt = excerpt(request.content());
        if (parentId == null) {
            // Root reply -> notify the thread author (skipped if the thread is anonymized).
            UUID recipient = thread.isDeleted() ? null : thread.getUserId();
            publishSocialEvent(recipient, userId,
                    new ForumThreadRepliedEvent(threadId, postId, recipient, userId, excerpt));
        } else {
            // Nested reply -> notify the PARENT reply's author only (never the thread author too).
            publishSocialEvent(parentAuthorId, userId,
                    new ForumReplyRepliedEvent(threadId, parentId, postId, parentAuthorId, userId, excerpt));
        }

        // Everything tagged on create is new, so every tagged person (bar the author) is notified.
        // A tagged user who is also the parent/thread author gets both notifications — different
        // events, different meanings.
        publishTagEvents(personIds, carIds, ownerByCar, userId,
                recipient -> new ForumReplyTaggedEvent(threadId, postId, recipient.userId(), userId, recipient.carTagged()));

        return toReplyDtos(List.of(hydrated), userId, thread.getUserId()).getFirst();
    }

    @Override
    @Transactional
    public ReplyDto updateReply(String currentUserId, UUID postId, UpdateReplyRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        ForumThreadReplyEntity post = postRepository.findById(postId)
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

        // PATCH semantics, same as updateThread: null leaves a tag set alone, non-null replaces it,
        // and the owner rule is validated against the resulting set.
        List<UUID> currentPersonIds = currentReplyTaggedPersonIds(postId);
        List<UUID> currentCarIds = currentReplyTaggedCarIds(postId);
        List<UUID> personIds = request.taggedPeople() != null ? distinctIds(request.taggedPeople()) : currentPersonIds;
        List<UUID> carIds = request.taggedCars() != null ? distinctIds(request.taggedCars()) : currentCarIds;
        if (request.taggedPeople() != null) {
            validateTaggedPeopleExist(personIds);
        }
        Map<UUID, UUID> ownerByCar = validateTaggedCars(post.getUserId(), personIds, carIds);

        post.setContent(request.content().strip());
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);

        if (request.taggedPeople() != null) {
            replyTaggedPersonRepository.deleteAllByIdReplyId(postId);
        }
        if (request.taggedCars() != null) {
            replyTaggedCarRepository.deleteAllByIdReplyId(postId);
        }
        entityManager.flush(); // the deletes must hit the DB before the re-inserts
        insertReplyTags(postId,
                request.taggedPeople() != null ? personIds : List.of(),
                request.taggedCars() != null ? carIds : List.of());
        entityManager.flush();

        // Only newly added tags notify — re-saving an unchanged tag set is silent.
        publishTagEvents(added(currentPersonIds, personIds), added(currentCarIds, carIds), ownerByCar, userId,
                recipient -> new ForumReplyTaggedEvent(post.getThreadId(), postId, recipient.userId(), userId, recipient.carTagged()));

        return toReplyDtos(List.of(post), userId, thread.getUserId()).getFirst();
    }

    // -------------------------------------------------------------------
    // WRITE — likes (idempotent; counts maintained by triggers)
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public void likeThread(String currentUserId, UUID threadId) {
        UUID userId = UUID.fromString(currentUserId);
        ForumThreadEntity thread = loadThread(threadId);
        // ON CONFLICT DO NOTHING: idempotent and race-safe against concurrent double-taps.
        int inserted = threadLikeRepository.insertIgnoringConflict(threadId, userId);
        if (inserted == 1) {
            // Only a real (first) like notifies; skipped if the thread is anonymized.
            UUID recipient = thread.isDeleted() ? null : thread.getUserId();
            publishSocialEvent(recipient, userId, new ForumThreadLikedEvent(threadId, recipient, userId));
        }
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
        ForumThreadReplyEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new ForumPostNotFoundException(postId));
        if (post.isDeleted()) {
            throw new ForumPostDeletedException(postId);
        }
        // ON CONFLICT DO NOTHING: idempotent and race-safe against concurrent double-taps.
        int inserted = postLikeRepository.insertIgnoringConflict(postId, userId);
        if (inserted == 1) {
            // Only a real (first) like notifies. A likeable reply is never deleted (checked above).
            publishSocialEvent(post.getUserId(), userId,
                    new ForumReplyLikedEvent(postId, post.getThreadId(), post.getUserId(), userId));
        }
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
        ForumThreadReplyEntity post = postRepository.findById(postId)
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
    private void removeReply(ForumThreadReplyEntity post) {
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
    public Optional<ModerationContentDto> findThreadModerationSnapshot(UUID threadId) {
        return threadRepository.findById(threadId).map(thread -> {
            String body = thread.getContent();
            String content = body == null || body.isBlank()
                    ? thread.getTitle()
                    : thread.getTitle() + "\n\n" + body;
            return new ModerationContentDto(
                    thread.getId(), thread.getUserId(), content, List.of(), thread.getCreatedAt());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ModerationContentDto> findReplyModerationSnapshot(UUID postId) {
        return postRepository.findById(postId)
                .map(post -> new ModerationContentDto(
                        post.getId(), post.getUserId(), post.getContent(),
                        List.of(), post.getCreatedAt()));
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
        ForumThreadReplyEntity post = postRepository.findById(postId)
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
            ForumThreadReplyEntity parent = postRepository.findById(parentId).orElse(null);
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
        ForumThreadReplyEntity post = postRepository.findById(postId)
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
                .collect(Collectors.toMap(ForumThreadTopicOptionsEntity::getId, this::toTopicDto));

        // Tagged people/cars, batched for the whole page (one query each) and resolved together with
        // the authors so a card page still costs a fixed number of queries.
        Map<UUID, List<UUID>> taggedPersonIdsByThread = threadTaggedPersonRepository.findAllByIdThreadIdIn(threadIds).stream()
                .collect(Collectors.groupingBy(tp -> tp.getId().getThreadId(),
                        Collectors.mapping(tp -> tp.getId().getUserId(), Collectors.toList())));
        Map<UUID, List<UUID>> taggedCarIdsByThread = threadTaggedCarRepository.findAllByIdThreadIdIn(threadIds).stream()
                .collect(Collectors.groupingBy(tc -> tc.getId().getThreadId(),
                        Collectors.mapping(tc -> tc.getId().getCarId(), Collectors.toList())));

        Set<UUID> authorIds = threads.stream()
                .filter(t -> !t.isDeleted())
                .map(ForumThreadEntity::getUserId).collect(Collectors.toSet());
        Set<UUID> profileIds = new HashSet<>(authorIds);
        taggedPersonIdsByThread.values().forEach(profileIds::addAll);
        Map<UUID, ProfileSearchResultDto> authors = profileIds.isEmpty() ? Map.of()
                : profileService.findByIds(profileIds).stream()
                        .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));

        Set<UUID> taggedCarIds = taggedCarIdsByThread.values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        Map<UUID, CarSummaryDto> taggedCars = taggedCarIds.isEmpty() ? Map.of()
                : garageService.findCarsByIds(taggedCarIds).stream()
                        .collect(Collectors.toMap(CarSummaryDto::id, Function.identity()));

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
                        resolve(taggedPersonIdsByThread.get(t.getId()), authors),
                        resolve(taggedCarIdsByThread.get(t.getId()), taggedCars),
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
    private CursorPage<ReplyDto> toReplyPage(List<ForumThreadReplyEntity> rows, int limit, UUID viewerId, UUID threadAuthorId) {
        boolean hasMore = rows.size() > limit;
        List<ForumThreadReplyEntity> page = hasMore ? rows.subList(0, limit) : rows;

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
    private List<ReplyDto> toReplyDtos(List<ForumThreadReplyEntity> posts, UUID viewerId, UUID threadAuthorId) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<UUID> postIds = posts.stream().map(ForumThreadReplyEntity::getId).toList();

        // A deleted reply is a placeholder: its tags are dropped from the DTO along with its author
        // and content, so they are not even looked up.
        List<UUID> visibleIds = posts.stream()
                .filter(p -> !p.isDeleted())
                .map(ForumThreadReplyEntity::getId).toList();
        Map<UUID, List<UUID>> taggedPersonIdsByReply = visibleIds.isEmpty() ? Map.of()
                : replyTaggedPersonRepository.findAllByIdReplyIdIn(visibleIds).stream()
                        .collect(Collectors.groupingBy(tp -> tp.getId().getReplyId(),
                                Collectors.mapping(tp -> tp.getId().getUserId(), Collectors.toList())));
        Map<UUID, List<UUID>> taggedCarIdsByReply = visibleIds.isEmpty() ? Map.of()
                : replyTaggedCarRepository.findAllByIdReplyIdIn(visibleIds).stream()
                        .collect(Collectors.groupingBy(tc -> tc.getId().getReplyId(),
                                Collectors.mapping(tc -> tc.getId().getCarId(), Collectors.toList())));

        Set<UUID> authorIds = posts.stream()
                .filter(p -> !p.isDeleted())
                .map(ForumThreadReplyEntity::getUserId).collect(Collectors.toSet());
        Set<UUID> profileIds = new HashSet<>(authorIds);
        taggedPersonIdsByReply.values().forEach(profileIds::addAll);
        Map<UUID, ProfileSearchResultDto> authors = profileIds.isEmpty() ? Map.of()
                : profileService.findByIds(profileIds).stream()
                        .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));

        Set<UUID> taggedCarIds = taggedCarIdsByReply.values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        Map<UUID, CarSummaryDto> taggedCars = taggedCarIds.isEmpty() ? Map.of()
                : garageService.findCarsByIds(taggedCarIds).stream()
                        .collect(Collectors.toMap(CarSummaryDto::id, Function.identity()));

        Set<UUID> liked = Set.copyOf(postRepository.findLikedPostIds(viewerId, postIds));

        return posts.stream()
                .map(p -> new ReplyDto(
                        p.getId(),
                        p.isDeleted() ? null : authors.get(p.getUserId()),
                        p.isDeleted() ? null : p.getContent(),
                        resolve(taggedPersonIdsByReply.get(p.getId()), authors),
                        resolve(taggedCarIdsByReply.get(p.getId()), taggedCars),
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
                .collect(Collectors.toMap(ForumThreadTopicOptionsEntity::getId, this::toTopicDto));

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

    private TopicDto toTopicDto(ForumThreadTopicOptionsEntity topic) {
        return new TopicDto(topic.getId(), topic.getName(), topic.getSortOrder(),
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
        List<ForumThreadTopicOptionsEntity> found = topicRepository.findAllById(topicIds);
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

    // -------------------------------------------------------------------
    // TAGGED PEOPLE & CARS (mirrors the posts module's tagging rules)
    // -------------------------------------------------------------------

    private void insertThreadTags(UUID threadId, List<UUID> personIds, List<UUID> carIds) {
        for (UUID personId : personIds) {
            ForumThreadTaggedPersonEntity tagged = new ForumThreadTaggedPersonEntity();
            tagged.setId(new ForumThreadTaggedPersonId(threadId, personId));
            threadTaggedPersonRepository.save(tagged);
        }
        for (UUID carId : carIds) {
            ForumThreadTaggedCarEntity tagged = new ForumThreadTaggedCarEntity();
            tagged.setId(new ForumThreadTaggedCarId(threadId, carId));
            threadTaggedCarRepository.save(tagged);
        }
    }

    private void insertReplyTags(UUID replyId, List<UUID> personIds, List<UUID> carIds) {
        for (UUID personId : personIds) {
            ForumReplyTaggedPersonEntity tagged = new ForumReplyTaggedPersonEntity();
            tagged.setId(new ForumReplyTaggedPersonId(replyId, personId));
            replyTaggedPersonRepository.save(tagged);
        }
        for (UUID carId : carIds) {
            ForumReplyTaggedCarEntity tagged = new ForumReplyTaggedCarEntity();
            tagged.setId(new ForumReplyTaggedCarId(replyId, carId));
            replyTaggedCarRepository.save(tagged);
        }
    }

    private List<UUID> currentThreadTaggedPersonIds(UUID threadId) {
        return threadTaggedPersonRepository.findAllByIdThreadId(threadId).stream()
                .map(tp -> tp.getId().getUserId())
                .toList();
    }

    private List<UUID> currentThreadTaggedCarIds(UUID threadId) {
        return threadTaggedCarRepository.findAllByIdThreadId(threadId).stream()
                .map(tc -> tc.getId().getCarId())
                .toList();
    }

    private List<UUID> currentReplyTaggedPersonIds(UUID replyId) {
        return replyTaggedPersonRepository.findAllByIdReplyId(replyId).stream()
                .map(tp -> tp.getId().getUserId())
                .toList();
    }

    private List<UUID> currentReplyTaggedCarIds(UUID replyId) {
        return replyTaggedCarRepository.findAllByIdReplyId(replyId).stream()
                .map(tc -> tc.getId().getCarId())
                .toList();
    }

    private void validateTaggedPeopleExist(List<UUID> personIds) {
        if (personIds.isEmpty()) {
            return;
        }
        if (profileService.findByIds(personIds).size() != personIds.size()) {
            throw new InvalidReferenceException("One or more tagged people do not exist");
        }
    }

    /**
     * Validates the tagged cars against the tagged people: every car must exist, and its owner must
     * be tagged in the same thread/reply — unless the owner is the author, who may tag their own
     * cars without self-tagging. This is what makes the "tag a user, then pick from their garage"
     * flow enforceable server-side (same rule as the posts module).
     *
     * @return each tagged car's owner id, reused by the caller to work out who to notify
     */
    private Map<UUID, UUID> validateTaggedCars(UUID authorId, List<UUID> personIds, List<UUID> carIds) {
        if (carIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UUID> ownerByCar = garageService.findCarOwnerIds(carIds);
        if (ownerByCar.size() != carIds.size()) {
            throw new InvalidReferenceException("One or more tagged cars do not exist");
        }

        Set<UUID> allowedOwners = new HashSet<>(personIds);
        allowedOwners.add(authorId);
        for (UUID carId : carIds) {
            if (!allowedOwners.contains(ownerByCar.get(carId))) {
                throw new CarOwnerNotTaggedException(carId);
            }
        }
        return ownerByCar;
    }

    /**
     * Publishes one tag event per user who is <em>newly</em> tagged — either directly or by having
     * one of their cars tagged (a car's owner is always tagged as a person too, so the two collapse
     * into a single notification carrying {@code carTagged}). Self-tags are dropped by
     * {@link #publishSocialEvent}.
     */
    private void publishTagEvents(List<UUID> newPersonIds, List<UUID> newCarIds, Map<UUID, UUID> ownerByCar,
                                  UUID actorId, Function<TagRecipient, Object> eventFactory) {
        Set<UUID> ownersOfNewCars = newCarIds.stream()
                .map(ownerByCar::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<UUID> recipients = new LinkedHashSet<>(newPersonIds);
        recipients.addAll(ownersOfNewCars);
        for (UUID recipientId : recipients) {
            Object event = eventFactory.apply(new TagRecipient(recipientId, ownersOfNewCars.contains(recipientId)));
            publishSocialEvent(recipientId, actorId, event);
        }
    }

    /** One notification target of a tagging action, and whether it was (also) one of their cars. */
    private record TagRecipient(UUID userId, boolean carTagged) {}

    /** The ids in {@code next} that were not already in {@code current}, in {@code next}'s order. */
    private static List<UUID> added(List<UUID> current, List<UUID> next) {
        Set<UUID> before = Set.copyOf(current);
        return next.stream().filter(id -> !before.contains(id)).toList();
    }

    /**
     * Maps a thread's / reply's tag id list onto the batch-resolved DTOs, dropping ids that no
     * longer resolve (a profile or car removed between the two queries).
     */
    private static <T> List<T> resolve(List<UUID> ids, Map<UUID, T> byId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
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

    /**
     * Publishes a social-notification event unless it would be a self-notification, or the recipient
     * is absent (a {@code null} recipient means the target content is anonymized / its author hidden).
     * The self-notify and null-recipient skips live here, one consistent place: the
     * {@code notification} listener assumes any event it receives has a real, non-actor recipient.
     */
    private void publishSocialEvent(UUID recipientId, UUID actorId, Object event) {
        if (recipientId != null && !recipientId.equals(actorId)) {
            eventPublisher.publishEvent(event);
        }
    }

    /** Trims reply text to a short notification-body excerpt, or {@code null} if blank. */
    private static String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.length() <= EXCERPT_MAX_LENGTH
                ? trimmed
                : trimmed.substring(0, EXCERPT_MAX_LENGTH) + "…";
    }

    private static List<String> distinct(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static List<UUID> distinctIds(List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
