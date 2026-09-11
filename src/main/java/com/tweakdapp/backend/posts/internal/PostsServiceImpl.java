package com.tweakdapp.backend.posts.internal;

import com.tweakdapp.backend.mapevents.MapEventContestsService;
import com.tweakdapp.backend.mapevents.dto.ParticipantCardDto;
import com.tweakdapp.backend.mapevents.dto.ParticipantCardKey;
import com.tweakdapp.backend.posts.dto.request.ShareParticipantCardRequest;
import com.tweakdapp.backend.posts.exception.ParticipantCardCooldownException;
import com.tweakdapp.backend.posts.exception.ParticipantCardNotFoundException;
import java.time.Duration;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.posts.PostCommentTaggedEvent;
import com.tweakdapp.backend.posts.PostCommentedEvent;
import com.tweakdapp.backend.posts.PostLikedEvent;
import com.tweakdapp.backend.posts.PostSharedEvent;
import com.tweakdapp.backend.posts.PostTaggedEvent;
import com.tweakdapp.backend.posts.PostsService;
import com.tweakdapp.backend.posts.dto.CommentDto;
import com.tweakdapp.backend.posts.dto.CommentPageDto;
import com.tweakdapp.backend.posts.dto.LikerPageDto;
import com.tweakdapp.backend.posts.dto.PostDto;
import com.tweakdapp.backend.posts.dto.PostImageDto;
import com.tweakdapp.backend.posts.dto.PostPageDto;
import com.tweakdapp.backend.posts.dto.request.CreateCommentRequest;
import com.tweakdapp.backend.posts.dto.request.CreatePostRequest;
import com.tweakdapp.backend.posts.dto.request.UpdatePostRequest;
import com.tweakdapp.backend.posts.exception.CannotReportOwnContentException;
import com.tweakdapp.backend.posts.exception.CarOwnerNotTaggedException;
import com.tweakdapp.backend.posts.exception.CommentNotFoundException;
import com.tweakdapp.backend.posts.exception.InvalidReferenceException;
import com.tweakdapp.backend.posts.exception.NotCommentOwnerException;
import com.tweakdapp.backend.posts.exception.NotPostOwnerException;
import com.tweakdapp.backend.posts.exception.PostNotFoundException;
import com.tweakdapp.backend.posts.internal.entities.CommentEntity;
import com.tweakdapp.backend.posts.internal.entities.CommentLikeEntity;
import com.tweakdapp.backend.posts.internal.entities.CommentLikeId;
import com.tweakdapp.backend.posts.internal.entities.CommentTaggedCarEntity;
import com.tweakdapp.backend.posts.internal.entities.CommentTaggedCarId;
import com.tweakdapp.backend.posts.internal.entities.CommentTaggedPersonEntity;
import com.tweakdapp.backend.posts.internal.entities.CommentTaggedPersonId;
import com.tweakdapp.backend.posts.internal.entities.PostEntity;
import com.tweakdapp.backend.posts.internal.entities.PostImageEntity;
import com.tweakdapp.backend.posts.internal.entities.PostLikeEntity;
import com.tweakdapp.backend.posts.internal.entities.PostLikeId;
import com.tweakdapp.backend.posts.internal.entities.PostShareEntity;
import com.tweakdapp.backend.posts.internal.entities.PostShareId;
import com.tweakdapp.backend.posts.internal.entities.SavedPostEntity;
import com.tweakdapp.backend.posts.internal.entities.SavedPostId;
import com.tweakdapp.backend.posts.internal.entities.TaggedCarEntity;
import com.tweakdapp.backend.posts.internal.entities.TaggedCarId;
import com.tweakdapp.backend.posts.internal.entities.TaggedPersonEntity;
import com.tweakdapp.backend.posts.internal.entities.TaggedPersonId;
import com.tweakdapp.backend.posts.internal.repositories.CommentLikeRepository;
import com.tweakdapp.backend.posts.internal.repositories.CommentRepository;
import com.tweakdapp.backend.posts.internal.repositories.CommentTaggedCarRepository;
import com.tweakdapp.backend.posts.internal.repositories.CommentTaggedPersonRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostImageRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostLikeRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostRepository;
import com.tweakdapp.backend.posts.internal.repositories.PostShareRepository;
import com.tweakdapp.backend.posts.internal.repositories.SavedPostRepository;
import com.tweakdapp.backend.posts.internal.repositories.TagRefRow;
import com.tweakdapp.backend.posts.internal.repositories.TaggedCarRepository;
import com.tweakdapp.backend.posts.internal.repositories.TaggedPersonRepository;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.profile.exception.ProfileNotFoundException;
import com.tweakdapp.backend.report.ReportService;
import com.tweakdapp.backend.shared.moderation.ModerationContentDto;
import com.tweakdapp.backend.shared.tagging.TaggedContentRef;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
public class PostsServiceImpl implements PostsService {

    private static final Logger log = LoggerFactory.getLogger(PostsServiceImpl.class);

    /** Hard cap on page size so a client can't request an unbounded page. */
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** Max length of the comment excerpt carried in a notification body. */
    private static final int EXCERPT_MAX_LENGTH = 80;

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final TaggedPersonRepository taggedPersonRepository;
    private final TaggedCarRepository taggedCarRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentTaggedPersonRepository commentTaggedPersonRepository;
    private final CommentTaggedCarRepository commentTaggedCarRepository;
    private final PostLikeRepository postLikeRepository;
    private final SavedPostRepository savedPostRepository;
    private final PostShareRepository postShareRepository;
    private final ProfileService profileService;
    private final GarageService garageService;
    private final StorageService storageService;
    private final ReportService reportService;
    private final ApplicationEventPublisher eventPublisher;
    private final MapEventContestsService mapEventContestsService;

    @PersistenceContext
    private EntityManager entityManager;

    public PostsServiceImpl(PostRepository postRepository,
                            PostImageRepository postImageRepository,
                            TaggedPersonRepository taggedPersonRepository,
                            TaggedCarRepository taggedCarRepository,
                            CommentRepository commentRepository,
                            CommentLikeRepository commentLikeRepository,
                            CommentTaggedPersonRepository commentTaggedPersonRepository,
                            CommentTaggedCarRepository commentTaggedCarRepository,
                            PostLikeRepository postLikeRepository,
                            SavedPostRepository savedPostRepository,
                            PostShareRepository postShareRepository,
                            ProfileService profileService,
                            GarageService garageService,
                            StorageService storageService,
                            ReportService reportService,
                            ApplicationEventPublisher eventPublisher,
                            MapEventContestsService mapEventContestsService) {
        this.postRepository = postRepository;
        this.postImageRepository = postImageRepository;
        this.taggedPersonRepository = taggedPersonRepository;
        this.taggedCarRepository = taggedCarRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.commentTaggedPersonRepository = commentTaggedPersonRepository;
        this.commentTaggedCarRepository = commentTaggedCarRepository;
        this.postLikeRepository = postLikeRepository;
        this.savedPostRepository = savedPostRepository;
        this.postShareRepository = postShareRepository;
        this.profileService = profileService;
        this.garageService = garageService;
        this.storageService = storageService;
        this.reportService = reportService;
        this.eventPublisher = eventPublisher;
        this.mapEventContestsService = mapEventContestsService;
    }

    // -------------------------------------------------------------------
    // POST CREATION & MEDIA
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public PostDto createPost(String currentUserId, CreatePostRequest request) {
        return doCreatePost(UUID.fromString(currentUserId), request, null);
    }

    /**
     * Minimum gap between two shares of the same participant card. Reposting is welcome — new content
     * keeps the feed alive — but not the same card ten times in an hour.
     */
    static final Duration PARTICIPANT_CARD_REPOST_COOLDOWN = Duration.ofHours(48);

    @Override
    @Transactional
    public PostDto shareParticipantCard(String currentUserId, ShareParticipantCardRequest request) {
        UUID userId = UUID.fromString(currentUserId);
        UUID eventId = request.eventId();
        UUID carId = request.carId();
        // The request only names the card. Whether it exists and is the caller's is decided here, and
        // every rank and count on it is derived server-side.
        mapEventContestsService.findOwnedParticipantCard(userId, eventId, carId)
                .orElseThrow(() -> new ParticipantCardNotFoundException(eventId, carId));

        // Held until commit, so a double tap cannot slip two posts past the check below.
        postRepository.lockParticipantCard(eventId + ":" + carId);
        Instant last = postRepository.findLastParticipantCardPostAt(eventId, carId);
        if (last != null) {
            Instant nextAllowed = last.plus(PARTICIPANT_CARD_REPOST_COOLDOWN);
            if (Instant.now().isBefore(nextAllowed)) {
                throw new ParticipantCardCooldownException(nextAllowed);
            }
        }

        // An ordinary post that tags the car — so it also shows among the car's tagged posts — plus
        // the card reference the feed draws the card from.
        CreatePostRequest post = new CreatePostRequest(
                request.description(), List.of(), List.of(carId), null, null, null, null);
        return doCreatePost(userId, post, new ParticipantCardKey(eventId, carId));
    }

    /** Shared by both create paths; {@code card} is non-null only when sharing a participant card. */
    private PostDto doCreatePost(UUID userId, CreatePostRequest request, ParticipantCardKey card) {
        List<UUID> personIds = distinctIds(request.taggedPeople());
        List<UUID> carIds = distinctIds(request.taggedCars());

        // Validate cross-module references up front so a bad tag fails the whole create.
        validateTaggedPeopleExist(personIds);
        Map<UUID, UUID> ownerByCar = validateTaggedCars(userId, personIds, carIds);

        UUID postId = UUID.randomUUID();
        PostEntity post = new PostEntity();
        post.setId(postId);
        post.setUserId(userId);
        // posts.description is NOT NULL; an empty caption is allowed, so coalesce null -> "".
        post.setDescription(request.description() == null ? "" : request.description());
        post.setLikesCountEnabled(orDefaultTrue(request.likesCountEnabled()));
        post.setCommentsCountEnabled(orDefaultTrue(request.commentsCountEnabled()));
        post.setSharesCountEnabled(orDefaultTrue(request.sharesCountEnabled()));
        post.setSavedCountEnabled(orDefaultTrue(request.savedCountEnabled()));
        post.setLikesCount(0L);
        post.setCommentsCount(0L);
        post.setSharesCount(0L);
        post.setQuoteSharesCount(0L);
        post.setSavedCount(0L);
        post.setUpdatedAt(Instant.now());
        if (card != null) {
            post.setParticipantCardEventId(card.eventId());
            post.setParticipantCardCarId(card.carId());
        }
        postRepository.save(post);

        insertTaggedPeople(postId, personIds);
        insertTaggedCars(postId, carIds);

        // Flush the INSERTs and clear the context so the re-fetch issues a real SELECT and picks
        // up DB-managed columns (e.g. createdAt) instead of the cached, partially-populated row.
        entityManager.flush();
        entityManager.clear();

        PostEntity hydrated = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        // Everything tagged on create is new, so every tagged person (bar the author) is notified.
        publishTagEvents(personIds, carIds, ownerByCar, userId,
                recipient -> new PostTaggedEvent(postId, recipient.userId(), userId, recipient.carTagged()));

        return toPostDto(hydrated, userId);
    }

    @Override
    @Transactional
    public PostDto updatePost(String currentUserId, UUID postId, UpdatePostRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        ensureOwnership(post, userId);

        // Validate the FINAL tag state before mutating anything. People and cars can be edited
        // independently, but the "a tagged car's owner must be tagged" rule couples them — so the
        // check must run over the post's resulting tags, using current DB tags for any list the
        // request leaves untouched (null).
        List<UUID> currentPersonIds = currentTaggedPersonIds(postId);
        List<UUID> currentCarIds = currentTaggedCarIds(postId);
        List<UUID> finalPersonIds = request.taggedPeople() != null
                ? distinctIds(request.taggedPeople())
                : currentPersonIds;
        List<UUID> finalCarIds = request.taggedCars() != null
                ? distinctIds(request.taggedCars())
                : currentCarIds;

        if (request.taggedPeople() != null) {
            validateTaggedPeopleExist(finalPersonIds);
        }
        Map<UUID, UUID> ownerByCar = validateTaggedCars(userId, finalPersonIds, finalCarIds);

        // Apply scalar fields: null means "leave unchanged".
        if (request.description() != null) {
            post.setDescription(request.description());
        }
        if (request.likesCountEnabled() != null) {
            post.setLikesCountEnabled(request.likesCountEnabled());
        }
        if (request.commentsCountEnabled() != null) {
            post.setCommentsCountEnabled(request.commentsCountEnabled());
        }
        if (request.sharesCountEnabled() != null) {
            post.setSharesCountEnabled(request.sharesCountEnabled());
        }
        if (request.savedCountEnabled() != null) {
            post.setSavedCountEnabled(request.savedCountEnabled());
        }
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);

        // A non-null tag list is the complete new set (replace-all); null leaves it untouched.
        if (request.taggedPeople() != null) {
            taggedPersonRepository.deleteAllByIdPostId(postId);
            insertTaggedPeople(postId, finalPersonIds);
        }
        if (request.taggedCars() != null) {
            taggedCarRepository.deleteAllByIdPostId(postId);
            insertTaggedCars(postId, finalCarIds);
        }

        entityManager.flush();
        entityManager.clear();

        PostEntity hydrated = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        // Only newly added tags notify — re-saving an unchanged tag set is silent.
        publishTagEvents(added(currentPersonIds, finalPersonIds), added(currentCarIds, finalCarIds),
                ownerByCar, userId,
                recipient -> new PostTaggedEvent(postId, recipient.userId(), userId, recipient.carTagged()));

        return toPostDto(hydrated, userId);
    }

    @Override
    @Transactional
    public void deletePost(String currentUserId, UUID postId) {
        UUID userId = UUID.fromString(currentUserId);

        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        ensureOwnership(post, userId);

        // Collect the post's R2 image keys BEFORE the rows disappear. The DB child rows (images,
        // tags, likes, shares, saves, comments, reports) are removed by the Supabase
        // ON DELETE CASCADE, but that cascade only touches the database, not R2.
        List<String> imageKeys = postImageRepository.findAllByPostIdOrderByDisplayOrderAsc(postId).stream()
                .map(PostImageEntity::getImageKey)
                .toList();

        postRepository.delete(post);

        deleteR2ObjectsAfterCommit(imageKeys, postId);
    }

    @Override
    @Transactional
    public PostDto savePostImageKeys(String currentUserId, UUID postId, List<String> imageKeys) {
        UUID userId = UUID.fromString(currentUserId);

        // Checks if the post exists in the database
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        // Checks if the current user is the owner of that post
        ensureOwnership(post, userId);

        // Diff: keys currently stored but absent from the incoming list are removed from R2.
        Set<String> incoming = new HashSet<>(imageKeys);
        List<String> removedKeys = postImageRepository.findAllByPostIdOrderByDisplayOrderAsc(postId).stream()
                .map(PostImageEntity::getImageKey)
                .filter(key -> !incoming.contains(key))
                .toList();

        // DB: replace-all to persist the final ordered state (position = list index).
        postImageRepository.deleteAllByPostId(postId);
        List<PostImageEntity> rows = new ArrayList<>();
        for (int i = 0; i < imageKeys.size(); i++) {
            PostImageEntity image = new PostImageEntity();
            image.setId(UUID.randomUUID());
            image.setPostId(postId);
            image.setImageKey(imageKeys.get(i));
            image.setDisplayOrder((short) i);
            rows.add(image);
        }
        postImageRepository.saveAll(rows);

        // R2: delete removed objects only after the DB transaction commits.
        deleteR2ObjectsAfterCommit(removedKeys, postId);

        entityManager.flush();
        entityManager.clear();

        PostEntity hydrated = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        return toPostDto(hydrated, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PostDto getPost(String currentUserId, UUID postId) {
        UUID viewerId = UUID.fromString(currentUserId);

        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        return toPostDto(post, viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public PostPageDto getMyPosts(String currentUserId, String cursor, int size) {
        UUID viewerId = UUID.fromString(currentUserId);
        return getPostsPage(viewerId, viewerId, cursor, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PostPageDto getUserPosts(String currentUserId, String username, String cursor, int size) {
        UUID viewerId = UUID.fromString(currentUserId);

        UUID authorId = profileService.findIdByUsername(username)
                .orElseThrow(() -> ProfileNotFoundException.byUsername(username));

        return getPostsPage(viewerId, authorId, cursor, size);
    }

    /**
     * Shared keyset paging + batch assembly for a single author's posts. The privacy decision is
     * the caller's; this method assumes access is already granted.
     */
    private PostPageDto getPostsPage(UUID viewerId, UUID authorId, String cursor, int size) {
        int limit = clampSize(size);
        PageCursor from = PageCursor.decode(cursor);

        List<PostEntity> rows = postRepository.findUserPostPage(
                authorId,
                from == null,
                from == null ? null : from.createdAt(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<PostEntity> page = hasMore ? rows.subList(0, limit) : rows;

        List<PostDto> items = toPostDtos(page, viewerId);
        String nextCursor = hasMore ? lastPostCursor(page) : null;
        return new PostPageDto(items, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public PostPageDto getRankedPosts(String currentUserId, String cursor, int size) {
        UUID viewerId = UUID.fromString(currentUserId);
        int limit = clampSize(size);
        RankCursor from = RankCursor.decode(cursor);

        List<PostEntity> rows = postRepository.findRankedPostPage(
                from == null,
                from == null ? null : from.rankingScore(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<PostEntity> page = hasMore ? rows.subList(0, limit) : rows;

        List<PostDto> items = toPostDtos(page, viewerId);
        String nextCursor = hasMore ? lastRankCursor(page) : null;
        return new PostPageDto(items, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public PostPageDto getSavedPosts(String currentUserId, String cursor, int size) {
        UUID viewerId = UUID.fromString(currentUserId);
        int limit = clampSize(size);
        PageCursor from = PageCursor.decode(cursor);

        List<SavedPostEntity> rows = savedPostRepository.findSavedPage(
                viewerId,
                from == null,
                from == null ? null : from.createdAt(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<SavedPostEntity> page = hasMore ? rows.subList(0, limit) : rows;

        List<UUID> postIds = page.stream().map(sp -> sp.getId().getPostId()).toList();
        List<PostDto> items = toPostDtosByIds(postIds, viewerId);
        String nextCursor = hasMore ? lastSavedCursor(page) : null;
        return new PostPageDto(items, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public PostPageDto getSharedPosts(String currentUserId, String cursor, int size) {
        UUID viewerId = UUID.fromString(currentUserId);
        int limit = clampSize(size);
        PageCursor from = PageCursor.decode(cursor);

        List<PostShareEntity> rows = postShareRepository.findSharedPage(
                viewerId,
                from == null,
                from == null ? null : from.createdAt(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<PostShareEntity> page = hasMore ? rows.subList(0, limit) : rows;

        List<UUID> postIds = page.stream().map(ps -> ps.getId().getPostId()).toList();
        List<PostDto> items = toPostDtosByIds(postIds, viewerId);
        String nextCursor = hasMore ? lastSharedCursor(page) : null;
        return new PostPageDto(items, nextCursor);
    }

    // -------------------------------------------------------------------
    // COMMENTS & LIKERS (paginated)
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CommentPageDto getComments(UUID viewerId, UUID postId, String cursor, int size) {
        int limit = clampSize(size);
        PageCursor from = PageCursor.decode(cursor);

        // Fetch one extra row to detect whether a further page exists.
        List<CommentEntity> rows = commentRepository.findRootCommentPage(
                postId,
                from == null,
                from == null ? null : from.createdAt(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        return toCommentPage(viewerId, rows, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPageDto getReplies(UUID viewerId, UUID postId, UUID commentId, String cursor, int size) {
        // Validate the parent comment exists and belongs to the post addressed in the URL.
        loadCommentOfPost(postId, commentId);

        int limit = clampSize(size);
        PageCursor from = PageCursor.decode(cursor);

        List<CommentEntity> rows = commentRepository.findReplyPage(
                commentId,
                from == null,
                from == null ? null : from.createdAt(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        return toCommentPage(viewerId, rows, limit);
    }

    /**
     * Trims the over-fetched page, batch-resolves comment authors, tags and the viewer's likes (no
     * per-row queries), and builds the {@link CommentPageDto}. Shared by the root-comment and
     * reply listings, which differ only in the keyset query that produced {@code rows}.
     */
    private CommentPageDto toCommentPage(UUID viewerId, List<CommentEntity> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<CommentEntity> page = hasMore ? rows.subList(0, limit) : rows;

        List<CommentDto> items = toCommentDtos(viewerId, page);

        String nextCursor = hasMore ? lastCommentCursor(page) : null;
        return new CommentPageDto(items, nextCursor);
    }

    /**
     * Assembles a batch of comments with a fixed, small number of queries (no N+1): one batch each
     * for the two tag join tables, one profile lookup covering authors and tagged people, one car
     * lookup, and one viewer-like lookup. Output preserves the input order.
     */
    private List<CommentDto> toCommentDtos(UUID viewerId, List<CommentEntity> page) {
        if (page.isEmpty()) {
            return List.of();
        }

        // A deleted comment is a "[deleted]" placeholder: its tags are dropped from the DTO along
        // with its content, so they are not even looked up.
        List<UUID> visibleIds = page.stream()
                .filter(c -> !c.isDeleted())
                .map(CommentEntity::getId).toList();
        Map<UUID, List<UUID>> taggedPersonIdsByComment = visibleIds.isEmpty() ? Map.of()
                : commentTaggedPersonRepository.findAllByIdCommentIdIn(visibleIds).stream()
                        .collect(Collectors.groupingBy(tp -> tp.getId().getCommentId(),
                                Collectors.mapping(tp -> tp.getId().getUserId(), Collectors.toList())));
        Map<UUID, List<UUID>> taggedCarIdsByComment = visibleIds.isEmpty() ? Map.of()
                : commentTaggedCarRepository.findAllByIdCommentIdIn(visibleIds).stream()
                        .collect(Collectors.groupingBy(tc -> tc.getId().getCommentId(),
                                Collectors.mapping(tc -> tc.getId().getCarId(), Collectors.toList())));

        Set<UUID> profileIds = page.stream().map(CommentEntity::getUserId).collect(Collectors.toCollection(HashSet::new));
        taggedPersonIdsByComment.values().forEach(profileIds::addAll);
        Map<UUID, ProfileSearchResultDto> authors = profileIds.isEmpty() ? Map.of()
                : profileService.findByIds(profileIds).stream()
                        .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));

        Set<UUID> taggedCarIds = taggedCarIdsByComment.values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        Map<UUID, CarSummaryDto> taggedCars = taggedCarIds.isEmpty() ? Map.of()
                : garageService.findCarsByIds(taggedCarIds).stream()
                        .collect(Collectors.toMap(CarSummaryDto::id, Function.identity()));

        List<UUID> commentIds = page.stream().map(CommentEntity::getId).toList();
        Set<UUID> likedByViewer = Set.copyOf(commentLikeRepository.findLikedCommentIds(viewerId, commentIds));

        return page.stream()
                .map(c -> new CommentDto(
                        c.getId(),
                        authors.get(c.getUserId()),
                        c.isDeleted() ? null : c.getContent(),
                        resolve(taggedPersonIdsByComment.get(c.getId()), authors),
                        resolve(taggedCarIdsByComment.get(c.getId()), taggedCars),
                        c.getParentCommentId(),
                        c.isDeleted(),
                        c.getLikesCount(),
                        likedByViewer.contains(c.getId()),
                        c.getCreatedAt(),
                        c.getReplyCount()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LikerPageDto getPostLikers(UUID postId, String cursor, int size) {
        int limit = clampSize(size);
        PageCursor from = PageCursor.decode(cursor);

        List<PostLikeEntity> rows = postLikeRepository.findLikerPage(
                postId,
                from == null,
                from == null ? null : from.createdAt(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<PostLikeEntity> page = hasMore ? rows.subList(0, limit) : rows;

        // Resolve profiles in one call, then re-order them to match the like ordering
        // (findByIds does not guarantee order).
        List<UUID> likerIds = page.stream().map(pl -> pl.getId().getUserId()).toList();
        Map<UUID, ProfileSearchResultDto> profiles = profileService.findByIds(likerIds).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));

        List<ProfileSearchResultDto> items = likerIds.stream()
                .map(profiles::get)
                .filter(Objects::nonNull)
                .toList();

        String nextCursor = hasMore ? lastLikerCursor(page) : null;
        return new LikerPageDto(items, nextCursor);
    }

    // -------------------------------------------------------------------
    // TAGS — the posts half of a profile's "tags" section
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<TaggedContentRef> findTaggedPostRefs(UUID userId, Collection<UUID> ownedCarIds,
                                                     Instant cursorTaggedAt, UUID cursorId, int limit) {
        boolean firstPage = cursorTaggedAt == null || cursorId == null;
        PageRequest page = PageRequest.of(0, Math.max(limit, 1));

        List<TagRefRow> rows = new ArrayList<>(taggedPersonRepository.findTaggedPostRefs(
                userId, firstPage, cursorTaggedAt, cursorId, page));
        if (ownedCarIds != null && !ownedCarIds.isEmpty()) {
            rows.addAll(taggedCarRepository.findTaggedPostRefs(
                    ownedCarIds, userId, firstPage, cursorTaggedAt, cursorId, page));
        }
        return mergeTagRefs(rows, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaggedContentRef> findTaggedCommentRefs(UUID userId, Collection<UUID> ownedCarIds,
                                                        Instant cursorTaggedAt, UUID cursorId, int limit) {
        boolean firstPage = cursorTaggedAt == null || cursorId == null;
        PageRequest page = PageRequest.of(0, Math.max(limit, 1));

        List<TagRefRow> rows = new ArrayList<>(commentTaggedPersonRepository.findTaggedCommentRefs(
                userId, firstPage, cursorTaggedAt, cursorId, page));
        if (ownedCarIds != null && !ownedCarIds.isEmpty()) {
            rows.addAll(commentTaggedCarRepository.findTaggedCommentRefs(
                    ownedCarIds, userId, firstPage, cursorTaggedAt, cursorId, page));
        }
        return mergeTagRefs(rows, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostDto> getPostsByIds(UUID viewerId, List<UUID> postIds) {
        return toPostDtosByIds(postIds, viewerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentDto> getCommentsByIds(UUID viewerId, List<UUID> commentIds) {
        if (commentIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, CommentEntity> byId = commentRepository.findAllById(commentIds).stream()
                .collect(Collectors.toMap(CommentEntity::getId, Function.identity()));
        List<CommentEntity> ordered = commentIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return toCommentDtos(viewerId, ordered);
    }

    @Override
    @Transactional
    public void removeSelfTagsFromPost(UUID userId, UUID postId) {
        ensurePostExists(postId);
        taggedPersonRepository.deleteByIdPostIdAndIdUserId(postId, userId);

        List<UUID> ownCarIds = garageService.findCarIdsByOwner(userId);
        if (!ownCarIds.isEmpty()) {
            taggedCarRepository.deleteByIdPostIdAndIdCarIdIn(postId, ownCarIds);
        }
    }

    @Override
    @Transactional
    public void removeSelfTagsFromComment(UUID userId, UUID commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new CommentNotFoundException(commentId);
        }
        commentTaggedPersonRepository.deleteByIdCommentIdAndIdUserId(commentId, userId);

        List<UUID> ownCarIds = garageService.findCarIdsByOwner(userId);
        if (!ownCarIds.isEmpty()) {
            commentTaggedCarRepository.deleteByIdCommentIdAndIdCarIdIn(commentId, ownCarIds);
        }
    }

    /**
     * Collapses the person-tag and car-tag streams of one surface into a single ordered page.
     * A user tagged alongside their own car produces a row in both streams, so the same content
     * is deduplicated here, keeping the later of the two tag timestamps.
     */
    private static List<TaggedContentRef> mergeTagRefs(List<TagRefRow> rows, int limit) {
        Map<UUID, TaggedContentRef> newestByTarget = new LinkedHashMap<>();
        for (TagRefRow row : rows) {
            newestByTarget.merge(
                    row.getTargetId(),
                    new TaggedContentRef(row.getTargetId(), row.getParentId(), row.getTaggedAt()),
                    (existing, candidate) -> candidate.taggedAt().isAfter(existing.taggedAt()) ? candidate : existing);
        }
        return newestByTarget.values().stream()
                .sorted(TaggedContentRef.NEWEST_FIRST)
                .limit(limit)
                .toList();
    }

    // -------------------------------------------------------------------
    // ENGAGEMENT — likes, saves, shares, comments
    //
    // The denormalized counts are all maintained by Supabase triggers on the engagement tables,
    // so these methods only insert / delete the engagement rows and never touch a count column.
    // The "add" operations are idempotent: a duplicate row is a no-op, not an error.
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public void likePost(String currentUserId, UUID postId) {
        UUID userId = UUID.fromString(currentUserId);
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (postLikeRepository.existsByIdPostIdAndIdUserId(postId, userId)) {
            return;
        }
        PostLikeEntity like = new PostLikeEntity();
        like.setId(new PostLikeId(postId, userId));
        postLikeRepository.save(like);
        // Only a real (first) like notifies; the early return above swallows re-likes.
        publishSocialEvent(post.getUserId(), userId, new PostLikedEvent(postId, post.getUserId(), userId));
    }

    @Override
    @Transactional
    public void unlikePost(String currentUserId, UUID postId) {
        UUID userId = UUID.fromString(currentUserId);
        postLikeRepository.deleteByIdPostIdAndIdUserId(postId, userId);
    }

    @Override
    @Transactional
    public void savePost(String currentUserId, UUID postId) {
        UUID userId = UUID.fromString(currentUserId);
        ensurePostExists(postId);
        if (savedPostRepository.existsByIdPostIdAndIdUserId(postId, userId)) {
            return;
        }
        SavedPostEntity saved = new SavedPostEntity();
        saved.setId(new SavedPostId(postId, userId));
        savedPostRepository.save(saved);
    }

    @Override
    @Transactional
    public void unsavePost(String currentUserId, UUID postId) {
        UUID userId = UUID.fromString(currentUserId);
        savedPostRepository.deleteByIdPostIdAndIdUserId(postId, userId);
    }

    @Override
    @Transactional
    public void sharePost(String currentUserId, UUID postId, String content) {
        UUID userId = UUID.fromString(currentUserId);
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (postShareRepository.existsByIdPostIdAndIdUserId(postId, userId)) {
            return;
        }
        PostShareEntity share = new PostShareEntity();
        share.setId(new PostShareId(postId, userId));
        // Blank caption -> null so it counts as a plain share (the trigger keys quote_shares_count
        // off a non-blank content).
        share.setContent(blankToNull(content));
        postShareRepository.save(share);
        // Only a real (first) share notifies; the early return above swallows re-shares.
        publishSocialEvent(post.getUserId(), userId, new PostSharedEvent(postId, post.getUserId(), userId));
    }

    @Override
    @Transactional
    public void unsharePost(String currentUserId, UUID postId) {
        UUID userId = UUID.fromString(currentUserId);
        postShareRepository.deleteByIdPostIdAndIdUserId(postId, userId);
    }

    @Override
    @Transactional
    public CommentDto addComment(String currentUserId, UUID postId, CreateCommentRequest request) {
        UUID userId = UUID.fromString(currentUserId);
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));

        UUID parentId = request.parentCommentId();
        if (parentId != null) {
            // A reply must thread under an existing comment on the same post.
            CommentEntity parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new CommentNotFoundException(parentId));
            if (!parent.getPostId().equals(postId)) {
                throw new CommentNotFoundException(parentId);
            }
        }

        // Same tagging rules as posts and forum threads/replies, validated before anything is written.
        List<UUID> personIds = distinctIds(request.taggedPeople());
        List<UUID> carIds = distinctIds(request.taggedCars());
        validateTaggedPeopleExist(personIds);
        Map<UUID, UUID> ownerByCar = validateTaggedCars(userId, personIds, carIds);

        UUID commentId = UUID.randomUUID();
        CommentEntity comment = new CommentEntity();
        comment.setId(commentId);
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(request.content());
        comment.setParentCommentId(parentId);
        comment.setDeleted(false);
        comment.setLikesCount(0);
        commentRepository.save(comment);

        insertCommentTags(commentId, personIds, carIds);

        // Flush the INSERT and clear the context so the re-fetch picks up the DB-managed createdAt.
        entityManager.flush();
        entityManager.clear();

        CommentEntity hydrated = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));

        // One profile batch for the author and everyone tagged.
        Set<UUID> profileIds = new LinkedHashSet<>();
        profileIds.add(userId);
        profileIds.addAll(personIds);
        Map<UUID, ProfileSearchResultDto> profiles = profileService.findByIds(profileIds).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));
        Map<UUID, CarSummaryDto> cars = carIds.isEmpty() ? Map.of()
                : garageService.findCarsByIds(carIds).stream()
                        .collect(Collectors.toMap(CarSummaryDto::id, Function.identity()));

        // Notify the POST author for any comment or reply, whatever the nesting.
        publishSocialEvent(post.getUserId(), userId,
                new PostCommentedEvent(postId, commentId, post.getUserId(), userId, excerpt(request.content())));

        // Comments can't be edited, so every tag here is new. A tagged user who is also the post's
        // author gets both notifications — different events, different meanings.
        publishTagEvents(personIds, carIds, ownerByCar, userId,
                recipient -> new PostCommentTaggedEvent(postId, commentId, recipient.userId(), userId,
                        recipient.carTagged()));

        return new CommentDto(
                hydrated.getId(),
                profiles.get(userId),
                hydrated.getContent(),
                resolve(personIds, profiles),
                resolve(carIds, cars),
                hydrated.getParentCommentId(),
                hydrated.isDeleted(),
                hydrated.getLikesCount(),
                false,
                hydrated.getCreatedAt(),
                hydrated.getReplyCount());
    }

    @Override
    @Transactional
    public void deleteComment(String currentUserId, UUID postId, UUID commentId) {
        UUID userId = UUID.fromString(currentUserId);

        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        CommentEntity comment = loadCommentOfPost(postId, commentId);

        // The comment's author may delete it; so may the post's owner, moderating their own post.
        boolean isCommentAuthor = comment.getUserId().equals(userId);
        boolean isPostOwner = post.getUserId().equals(userId);
        if (!isCommentAuthor && !isPostOwner) {
            throw new NotCommentOwnerException();
        }
        if (comment.isDeleted()) {
            return;
        }
        // Soft-delete: keep the row (it may still anchor replies); the trigger decrements
        // comments_count when the comment flips to deleted.
        comment.setDeleted(true);
        commentRepository.save(comment);
    }

    @Override
    @Transactional
    public void likeComment(String currentUserId, UUID postId, UUID commentId) {
        UUID userId = UUID.fromString(currentUserId);
        loadCommentOfPost(postId, commentId);
        if (commentLikeRepository.existsByIdCommentIdAndIdUserId(commentId, userId)) {
            return;
        }
        CommentLikeEntity like = new CommentLikeEntity();
        like.setId(new CommentLikeId(commentId, userId));
        commentLikeRepository.save(like);
    }

    @Override
    @Transactional
    public void unlikeComment(String currentUserId, UUID postId, UUID commentId) {
        UUID userId = UUID.fromString(currentUserId);
        commentLikeRepository.deleteByIdCommentIdAndIdUserId(commentId, userId);
    }

    // -------------------------------------------------------------------
    // REPORTING
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public void reportPost(String currentUserId, UUID postId, UUID reasonId) {
        UUID reporterId = UUID.fromString(currentUserId);
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (post.getUserId().equals(reporterId)) {
            throw new CannotReportOwnContentException();
        }
        reportService.reportPost(reporterId, postId, reasonId);
    }

    @Override
    @Transactional
    public void reportComment(String currentUserId, UUID postId, UUID commentId, UUID reasonId) {
        UUID reporterId = UUID.fromString(currentUserId);
        CommentEntity comment = loadCommentOfPost(postId, commentId);
        if (comment.getUserId().equals(reporterId)) {
            throw new CannotReportOwnContentException();
        }
        reportService.reportComment(reporterId, commentId, reasonId);
    }

    // -------------------------------------------------------------------
    // MODERATION — called by the admin module (which does the auth)
    // -------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<ModerationContentDto> findPostModerationSnapshot(UUID postId) {
        return postRepository.findById(postId).map(post -> {
            List<String> imageUrls = postImageRepository.findAllByPostIdOrderByDisplayOrderAsc(postId).stream()
                    .map(img -> storageService.publicUrl(StorageBucket.POSTS, img.getImageKey()))
                    .toList();
            return new ModerationContentDto(
                    post.getId(), post.getUserId(), post.getDescription(), imageUrls, post.getCreatedAt());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ModerationContentDto> findCommentModerationSnapshot(UUID commentId) {
        return commentRepository.findById(commentId)
                .map(comment -> new ModerationContentDto(
                        comment.getId(), comment.getUserId(), comment.getContent(),
                        List.of(), comment.getCreatedAt()));
    }

    @Override
    @Transactional
    public void deletePostAsModerator(UUID postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        // Same as deletePost, minus the ownership check: collect R2 keys before the cascade
        // removes the image rows, then clean up R2 after commit.
        List<String> imageKeys = postImageRepository.findAllByPostIdOrderByDisplayOrderAsc(postId).stream()
                .map(PostImageEntity::getImageKey)
                .toList();
        postRepository.delete(post);
        deleteR2ObjectsAfterCommit(imageKeys, postId);
    }

    @Override
    @Transactional
    public void deleteCommentAsModerator(UUID commentId) {
        CommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        if (comment.isDeleted()) {
            return;
        }
        comment.setDeleted(true);
        commentRepository.save(comment);
    }

    // -------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------

    /**
     * Assembles the full {@link PostDto} for a single post: resolves the author and tagged people
     * through the profile module, tagged cars through the garage module, builds image URLs from
     * stored R2 keys, and computes the viewer's like / save state.
     */
    private PostDto toPostDto(PostEntity post, UUID viewerId) {
        return toPostDtos(List.of(post), viewerId).getFirst();
    }

    /**
     * Loads the given posts by id and assembles them in the order of {@code postIds} (the order in
     * which the saved/shared keyset query returned them). {@code findAllById} doesn't preserve
     * order, so we re-index and re-order before delegating to {@link #toPostDtos}.
     */
    private List<PostDto> toPostDtosByIds(List<UUID> postIds, UUID viewerId) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, PostEntity> byId = postRepository.findAllById(postIds).stream()
                .collect(Collectors.toMap(PostEntity::getId, Function.identity()));
        List<PostEntity> ordered = postIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return toPostDtos(ordered, viewerId);
    }

    /**
     * Assembles a whole page of posts with a fixed, small number of queries regardless of page
     * size (no N+1): one batch each for tagged people, tagged cars, and images; one
     * {@code profileService.findByIds} covering all authors + tagged people combined; one
     * {@code garageService.findCarsByIds}; and one viewer like / save lookup. Output preserves
     * the input order.
     */
    private List<PostDto> toPostDtos(List<PostEntity> posts, UUID viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }

        List<UUID> postIds = posts.stream().map(PostEntity::getId).toList();

        Map<UUID, List<UUID>> personIdsByPost = taggedPersonRepository.findAllByIdPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(tp -> tp.getId().getPostId(),
                        Collectors.mapping(tp -> tp.getId().getUserId(), Collectors.toList())));
        Map<UUID, List<UUID>> carIdsByPost = taggedCarRepository.findAllByIdPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(tc -> tc.getId().getPostId(),
                        Collectors.mapping(tc -> tc.getId().getCarId(), Collectors.toList())));
        Map<UUID, List<PostImageEntity>> imagesByPost = postImageRepository
                .findAllByPostIdInOrderByPostIdAscDisplayOrderAsc(postIds).stream()
                .collect(Collectors.groupingBy(PostImageEntity::getPostId));

        // One profile lookup for every author and every tagged person across the page.
        Set<UUID> profileIds = new HashSet<>();
        posts.forEach(p -> profileIds.add(p.getUserId()));
        personIdsByPost.values().forEach(profileIds::addAll);
        Map<UUID, ProfileSearchResultDto> profiles = profileService.findByIds(profileIds).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));

        // One car lookup for every tagged car across the page.
        Set<UUID> allCarIds = new HashSet<>();
        carIdsByPost.values().forEach(allCarIds::addAll);
        Map<UUID, CarSummaryDto> cars = garageService.findCarsByIds(allCarIds).stream()
                .collect(Collectors.toMap(CarSummaryDto::id, Function.identity()));

        Set<UUID> likedByViewer = Set.copyOf(postLikeRepository.findLikedPostIds(viewerId, postIds));
        Set<UUID> savedByViewer = Set.copyOf(savedPostRepository.findSavedPostIds(viewerId, postIds));

        // One batch for every participant card on the page. Not viewer-scoped — a card reads the same
        // for everyone. A card that no longer derives (car gone, entry revoked) is simply absent and
        // its post renders as a plain one.
        Map<ParticipantCardKey, ParticipantCardDto> cards = mapEventContestsService.findParticipantCards(
                posts.stream()
                        .filter(p -> p.getParticipantCardEventId() != null)
                        .map(p -> new ParticipantCardKey(p.getParticipantCardEventId(), p.getParticipantCardCarId()))
                        .collect(Collectors.toSet()));

        return posts.stream()
                .map(post -> {
                    UUID postId = post.getId();

                    List<ProfileSearchResultDto> taggedPeople = personIdsByPost.getOrDefault(postId, List.of()).stream()
                            .map(profiles::get)
                            .filter(Objects::nonNull)
                            .toList();
                    List<CarSummaryDto> taggedCars = carIdsByPost.getOrDefault(postId, List.of()).stream()
                            .map(cars::get)
                            .filter(Objects::nonNull)
                            .toList();
                    List<PostImageDto> images = imagesByPost.getOrDefault(postId, List.of()).stream()
                            .map(img -> new PostImageDto(
                                    img.getId(),
                                    storageService.publicUrl(StorageBucket.POSTS, img.getImageKey()),
                                    img.getDisplayOrder()))
                            .toList();

                    // Plain shares and quote shares (re-shares with a custom caption) are tracked
                    // separately but shown as one total in the post card.
                    long totalShares = post.getSharesCount() + post.getQuoteSharesCount();

                    return new PostDto(
                            post.getId(),
                            post.getDescription(),
                            profiles.get(post.getUserId()),
                            images,
                            taggedPeople,
                            taggedCars,
                            post.getLikesCount(),
                            post.getCommentsCount(),
                            totalShares,
                            post.getSavedCount(),
                            post.isLikesCountEnabled(),
                            post.isCommentsCountEnabled(),
                            post.isSharesCountEnabled(),
                            post.isSavedCountEnabled(),
                            likedByViewer.contains(postId),
                            savedByViewer.contains(postId),
                            post.getCreatedAt(),
                            post.getUpdatedAt(),
                            post.getParticipantCardEventId() == null ? null
                                    : cards.get(new ParticipantCardKey(
                                            post.getParticipantCardEventId(), post.getParticipantCardCarId())));
                })
                .toList();
    }

    /** Verifies the user owns the post; only the author may mutate it. */
    private void ensureOwnership(PostEntity post, UUID userId) {
        if (!post.getUserId().equals(userId)) {
            throw new NotPostOwnerException();
        }
    }

    /** Cheap existence check for the engagement writes, which don't need to load the post row. */
    private void ensurePostExists(UUID postId) {
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException(postId);
        }
    }

    /**
     * Loads a comment and verifies it belongs to the given post; a comment addressed under the
     * wrong post is treated as not found.
     */
    private CommentEntity loadCommentOfPost(UUID postId, UUID commentId) {
        CommentEntity comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        if (!comment.getPostId().equals(postId)) {
            throw new CommentNotFoundException(commentId);
        }
        return comment;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    /**
     * Publishes a social-notification event unless it would be a self-notification. The self-notify
     * skip lives here (one consistent place): the {@code notification} listener assumes any event it
     * receives already has a real, non-actor recipient. A {@code null} recipient is also skipped
     * defensively (a post always has an author, but forums content can be anonymized).
     */
    private void publishSocialEvent(UUID recipientId, UUID actorId, Object event) {
        if (recipientId != null && !recipientId.equals(actorId)) {
            eventPublisher.publishEvent(event);
        }
    }

    /** Trims comment/reply text to a short notification-body excerpt, or {@code null} if blank. */
    private static String excerpt(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.length() <= EXCERPT_MAX_LENGTH
                ? trimmed
                : trimmed.substring(0, EXCERPT_MAX_LENGTH) + "…";
    }

    private void insertTaggedPeople(UUID postId, List<UUID> personIds) {
        for (UUID personId : personIds) {
            TaggedPersonEntity tagged = new TaggedPersonEntity();
            tagged.setId(new TaggedPersonId(postId, personId));
            taggedPersonRepository.save(tagged);
        }
    }

    private void insertTaggedCars(UUID postId, List<UUID> carIds) {
        for (UUID carId : carIds) {
            TaggedCarEntity tagged = new TaggedCarEntity();
            tagged.setId(new TaggedCarId(postId, carId));
            taggedCarRepository.save(tagged);
        }
    }

    private void insertCommentTags(UUID commentId, List<UUID> personIds, List<UUID> carIds) {
        for (UUID personId : personIds) {
            CommentTaggedPersonEntity tagged = new CommentTaggedPersonEntity();
            tagged.setId(new CommentTaggedPersonId(commentId, personId));
            commentTaggedPersonRepository.save(tagged);
        }
        for (UUID carId : carIds) {
            CommentTaggedCarEntity tagged = new CommentTaggedCarEntity();
            tagged.setId(new CommentTaggedCarId(commentId, carId));
            commentTaggedCarRepository.save(tagged);
        }
    }

    private void validateTaggedPeopleExist(List<UUID> personIds) {
        if (personIds.isEmpty()) {
            return;
        }
        long found = profileService.findByIds(personIds).size();
        if (found != personIds.size()) {
            throw new InvalidReferenceException("One or more tagged people do not exist");
        }
    }

    /**
     * Validates the tagged cars against the tagged people of the same post or comment: every car
     * must exist, and its owner must be tagged alongside it — unless the owner is the author, who
     * may tag their own cars without self-tagging. This is what makes the "tag a user, then pick
     * their cars" flow enforceable server-side.
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
     * one of their cars tagged (a car's owner is tagged as a person too unless the car is the
     * author's own, so the two collapse into a single notification carrying {@code carTagged}).
     * Self-tags are dropped by {@link #publishSocialEvent}.
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
     * Maps a comment's tag id list onto the batch-resolved DTOs, dropping ids that no longer
     * resolve (a profile or car removed between the two queries).
     */
    private static <T> List<T> resolve(List<UUID> ids, Map<UUID, T> byId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private List<UUID> currentTaggedPersonIds(UUID postId) {
        return taggedPersonRepository.findAllByIdPostId(postId).stream()
                .map(tp -> tp.getId().getUserId())
                .toList();
    }

    private List<UUID> currentTaggedCarIds(UUID postId) {
        return taggedCarRepository.findAllByIdPostId(postId).stream()
                .map(tc -> tc.getId().getCarId())
                .toList();
    }

    private static boolean orDefaultTrue(Boolean value) {
        return value == null || value;
    }

    private static List<UUID> distinctIds(List<UUID> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    /**
     * Registers an after-commit callback that deletes the given objects from R2. Deletion runs
     * only if the surrounding DB transaction commits; if R2 deletion fails afterwards the DB is
     * already consistent and we just log the orphaned keys.
     */
    private void deleteR2ObjectsAfterCommit(List<String> keys, UUID postId) {
        if (keys.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.deleteByKeys(StorageBucket.POSTS, keys);
                } catch (Exception e) {
                    log.warn("DB committed but failed to delete {} orphaned R2 objects for post {}",
                            keys.size(), postId, e);
                }
            }
        });
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String lastPostCursor(List<PostEntity> page) {
        PostEntity last = page.getLast();
        return new PageCursor(last.getCreatedAt(), last.getId()).encode();
    }

    private String lastRankCursor(List<PostEntity> page) {
        PostEntity last = page.getLast();
        return new RankCursor(last.getRankingScore(), last.getId()).encode();
    }

    private String lastCommentCursor(List<CommentEntity> page) {
        CommentEntity last = page.getLast();
        return new PageCursor(last.getCreatedAt(), last.getId()).encode();
    }

    private String lastSavedCursor(List<SavedPostEntity> page) {
        SavedPostEntity last = page.getLast();
        return new PageCursor(last.getCreatedAt(), last.getId().getPostId()).encode();
    }

    private String lastSharedCursor(List<PostShareEntity> page) {
        PostShareEntity last = page.getLast();
        return new PageCursor(last.getCreatedAt(), last.getId().getPostId()).encode();
    }

    private String lastLikerCursor(List<PostLikeEntity> page) {
        PostLikeEntity last = page.getLast();
        return new PageCursor(last.getCreatedAt(), last.getId().getUserId()).encode();
    }
}