package com.carsocialmedia.backend.posts.internal;

import com.carsocialmedia.backend.garage.GarageService;
import com.carsocialmedia.backend.garage.dto.CarSummaryDto;
import com.carsocialmedia.backend.posts.PostsService;
import com.carsocialmedia.backend.posts.dto.CommentDto;
import com.carsocialmedia.backend.posts.dto.CommentPageDto;
import com.carsocialmedia.backend.posts.dto.LikerPageDto;
import com.carsocialmedia.backend.posts.dto.PostDto;
import com.carsocialmedia.backend.posts.dto.PostImageDto;
import com.carsocialmedia.backend.posts.dto.PostPageDto;
import com.carsocialmedia.backend.posts.dto.request.CreatePostRequest;
import com.carsocialmedia.backend.posts.dto.request.UpdatePostRequest;
import com.carsocialmedia.backend.posts.exception.CarOwnerNotTaggedException;
import com.carsocialmedia.backend.posts.exception.InvalidReferenceException;
import com.carsocialmedia.backend.posts.exception.NotPostOwnerException;
import com.carsocialmedia.backend.posts.exception.PostNotFoundException;
import com.carsocialmedia.backend.posts.exception.PrivatePostException;
import com.carsocialmedia.backend.posts.internal.entities.CommentEntity;
import com.carsocialmedia.backend.posts.internal.entities.PostEntity;
import com.carsocialmedia.backend.posts.internal.entities.PostImageEntity;
import com.carsocialmedia.backend.posts.internal.entities.PostLikeEntity;
import com.carsocialmedia.backend.posts.internal.entities.TaggedCarEntity;
import com.carsocialmedia.backend.posts.internal.entities.TaggedCarId;
import com.carsocialmedia.backend.posts.internal.entities.TaggedPersonEntity;
import com.carsocialmedia.backend.posts.internal.entities.TaggedPersonId;
import com.carsocialmedia.backend.posts.internal.repositories.CommentLikeRepository;
import com.carsocialmedia.backend.posts.internal.repositories.CommentRepository;
import com.carsocialmedia.backend.posts.internal.repositories.PostImageRepository;
import com.carsocialmedia.backend.posts.internal.repositories.PostLikeRepository;
import com.carsocialmedia.backend.posts.internal.repositories.PostRepository;
import com.carsocialmedia.backend.posts.internal.repositories.SavedPostRepository;
import com.carsocialmedia.backend.posts.internal.repositories.TaggedCarRepository;
import com.carsocialmedia.backend.posts.internal.repositories.TaggedPersonRepository;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import com.carsocialmedia.backend.profile.exception.ProfileNotFoundException;
import com.carsocialmedia.backend.relationships.RelationshipService;
import com.carsocialmedia.backend.storage.StorageBucket;
import com.carsocialmedia.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final PostRepository postRepository;
    private final PostImageRepository postImageRepository;
    private final TaggedPersonRepository taggedPersonRepository;
    private final TaggedCarRepository taggedCarRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostLikeRepository postLikeRepository;
    private final SavedPostRepository savedPostRepository;
    private final ProfileService profileService;
    private final GarageService garageService;
    private final RelationshipService relationshipService;
    private final StorageService storageService;

    @PersistenceContext
    private EntityManager entityManager;

    public PostsServiceImpl(PostRepository postRepository,
                            PostImageRepository postImageRepository,
                            TaggedPersonRepository taggedPersonRepository,
                            TaggedCarRepository taggedCarRepository,
                            CommentRepository commentRepository,
                            CommentLikeRepository commentLikeRepository,
                            PostLikeRepository postLikeRepository,
                            SavedPostRepository savedPostRepository,
                            ProfileService profileService,
                            GarageService garageService,
                            RelationshipService relationshipService,
                            StorageService storageService) {
        this.postRepository = postRepository;
        this.postImageRepository = postImageRepository;
        this.taggedPersonRepository = taggedPersonRepository;
        this.taggedCarRepository = taggedCarRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.postLikeRepository = postLikeRepository;
        this.savedPostRepository = savedPostRepository;
        this.profileService = profileService;
        this.garageService = garageService;
        this.relationshipService = relationshipService;
        this.storageService = storageService;
    }

    // -------------------------------------------------------------------
    // POST CREATION & MEDIA
    // -------------------------------------------------------------------

    @Override
    @Transactional
    public PostDto createPost(String currentUserId, CreatePostRequest request) {
        UUID userId = UUID.fromString(currentUserId);

        List<UUID> personIds = distinctIds(request.taggedPeople());
        List<UUID> carIds = distinctIds(request.taggedCars());

        // Validate cross-module references up front so a bad tag fails the whole create.
        validateTaggedPeopleExist(personIds);
        validateTaggedCars(userId, personIds, carIds);

        UUID postId = UUID.randomUUID();
        PostEntity post = new PostEntity();
        post.setId(postId);
        post.setUserId(userId);
        // posts.description is NOT NULL; an empty caption is allowed, so coalesce null -> "".
        post.setDescription(request.description() == null ? "" : request.description());
        post.setLikesCountEnabled(orDefaultTrue(request.likesCountEnabled()));
        post.setCommentsCountEnabled(orDefaultTrue(request.commentsCountEnabled()));
        post.setSharesCountEnabled(orDefaultTrue(request.sharesCountEnabled()));
        post.setLikesCount(0L);
        post.setCommentsCount(0L);
        post.setSharesCount(0L);
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);

        insertTaggedPeople(postId, personIds);
        insertTaggedCars(postId, carIds);

        // Flush the INSERTs and clear the context so the re-fetch issues a real SELECT and picks
        // up DB-managed columns (e.g. createdAt) instead of the cached, partially-populated row.
        entityManager.flush();
        entityManager.clear();

        PostEntity hydrated = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
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
        List<UUID> finalPersonIds = request.taggedPeople() != null
                ? distinctIds(request.taggedPeople())
                : currentTaggedPersonIds(postId);
        List<UUID> finalCarIds = request.taggedCars() != null
                ? distinctIds(request.taggedCars())
                : currentTaggedCarIds(postId);

        if (request.taggedPeople() != null) {
            validateTaggedPeopleExist(finalPersonIds);
        }
        validateTaggedCars(userId, finalPersonIds, finalCarIds);

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

        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
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

        UUID authorId = post.getUserId();
        if (!viewerId.equals(authorId)) {
            ensureCanViewPost(viewerId, authorId);
        }

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

        if (!viewerId.equals(authorId)) {
            ensureCanViewPost(viewerId, authorId);
        }

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
                from == null ? null : from.createdAt(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<PostEntity> page = hasMore ? rows.subList(0, limit) : rows;

        List<PostDto> items = toPostDtos(page, viewerId);
        String nextCursor = hasMore ? lastPostCursor(page) : null;
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
                from == null ? null : from.createdAt(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<CommentEntity> page = hasMore ? rows.subList(0, limit) : rows;

        // Batch-resolve authors and the viewer's likes across the whole page (no per-row queries).
        List<UUID> authorIds = page.stream().map(CommentEntity::getUserId).distinct().toList();
        Map<UUID, ProfileSearchResultDto> authors = profileService.findByIds(authorIds).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));

        List<UUID> commentIds = page.stream().map(CommentEntity::getId).toList();
        Set<UUID> likedByViewer = Set.copyOf(commentLikeRepository.findLikedCommentIds(viewerId, commentIds));

        List<CommentDto> items = page.stream()
                .map(c -> new CommentDto(
                        c.getId(),
                        authors.get(c.getUserId()),
                        c.isDeleted() ? null : c.getContent(),
                        c.getParentCommentId(),
                        c.isDeleted(),
                        c.getLikesCount(),
                        likedByViewer.contains(c.getId()),
                        c.getCreatedAt()))
                .toList();

        String nextCursor = hasMore ? lastCommentCursor(page) : null;
        return new CommentPageDto(items, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public LikerPageDto getPostLikers(UUID postId, String cursor, int size) {
        int limit = clampSize(size);
        PageCursor from = PageCursor.decode(cursor);

        List<PostLikeEntity> rows = postLikeRepository.findLikerPage(
                postId,
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

                    return new PostDto(
                            post.getId(),
                            post.getDescription(),
                            profiles.get(post.getUserId()),
                            images,
                            taggedPeople,
                            taggedCars,
                            post.getLikesCount(),
                            post.getCommentsCount(),
                            post.getSharesCount(),
                            post.isLikesCountEnabled(),
                            post.isCommentsCountEnabled(),
                            post.isSharesCountEnabled(),
                            likedByViewer.contains(postId),
                            savedByViewer.contains(postId),
                            post.getCreatedAt(),
                            post.getUpdatedAt());
                })
                .toList();
    }

    /** Verifies the user owns the post; only the author may mutate it. */
    private void ensureOwnership(PostEntity post, UUID userId) {
        if (!post.getUserId().equals(userId)) {
            throw new NotPostOwnerException();
        }
    }

    /**
     * Gate for reading a post by a different author: public authors are always visible; a private
     * author's posts require the viewer to be an accepted follower. Mirrors the garage gate.
     */
    private void ensureCanViewPost(UUID viewerId, UUID authorId) {
        if (!profileService.isPrivate(authorId)) {
            return;
        }
        if (!relationshipService.isAcceptedFollower(viewerId, authorId)) {
            throw new PrivatePostException();
        }
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
     * Validates the tagged cars against the post's tagged people: every car must exist, and its
     * owner must be tagged in the post — unless the owner is the author, who may tag their own
     * cars without self-tagging. This is what makes the "tag a user, then pick their cars" flow
     * enforceable server-side.
     */
    private void validateTaggedCars(UUID authorId, List<UUID> personIds, List<UUID> carIds) {
        if (carIds.isEmpty()) {
            return;
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

    private String lastCommentCursor(List<CommentEntity> page) {
        CommentEntity last = page.getLast();
        return new PageCursor(last.getCreatedAt(), last.getId()).encode();
    }

    private String lastLikerCursor(List<PostLikeEntity> page) {
        PostLikeEntity last = page.getLast();
        return new PageCursor(last.getCreatedAt(), last.getId().getUserId()).encode();
    }
}