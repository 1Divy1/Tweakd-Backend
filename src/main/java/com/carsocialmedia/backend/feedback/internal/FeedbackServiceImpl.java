package com.carsocialmedia.backend.feedback.internal;

import com.carsocialmedia.backend.feedback.FeedbackService;
import com.carsocialmedia.backend.feedback.dto.AdminFeedbackDto;
import com.carsocialmedia.backend.feedback.dto.AdminFeedbackPageDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackBoardItemDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackBoardPageDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackCommentDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackCommentPageDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackCommentRequest;
import com.carsocialmedia.backend.feedback.dto.FeedbackFeatureDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackRequest;
import com.carsocialmedia.backend.feedback.dto.FeedbackStatsDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackStatusChangeDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackStatusDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackTypeDto;
import com.carsocialmedia.backend.feedback.dto.MyFeedbackDto;
import com.carsocialmedia.backend.feedback.exception.FeedbackCommentNotFoundException;
import com.carsocialmedia.backend.feedback.exception.FeedbackNotFoundException;
import com.carsocialmedia.backend.feedback.exception.InvalidFeedbackFeatureException;
import com.carsocialmedia.backend.feedback.exception.InvalidFeedbackStatusException;
import com.carsocialmedia.backend.feedback.exception.InvalidFeedbackTypeException;
import com.carsocialmedia.backend.feedback.exception.NotFeedbackCommentAuthorException;
import com.carsocialmedia.backend.feedback.internal.entities.FeedbackCommentEntity;
import com.carsocialmedia.backend.feedback.internal.entities.FeedbackEntity;
import com.carsocialmedia.backend.feedback.internal.entities.FeedbackFeatureOptionEntity;
import com.carsocialmedia.backend.feedback.internal.entities.FeedbackStatusOptionEntity;
import com.carsocialmedia.backend.feedback.internal.entities.FeedbackTypeOptionEntity;
import com.carsocialmedia.backend.feedback.internal.repositories.FeedbackCommentRepository;
import com.carsocialmedia.backend.feedback.internal.repositories.FeedbackFeatureOptionRepository;
import com.carsocialmedia.backend.feedback.internal.repositories.FeedbackRepository;
import com.carsocialmedia.backend.feedback.internal.repositories.FeedbackRepository.TrendingRow;
import com.carsocialmedia.backend.feedback.internal.repositories.FeedbackStatusOptionRepository;
import com.carsocialmedia.backend.feedback.internal.repositories.FeedbackSubscriptionRepository;
import com.carsocialmedia.backend.feedback.internal.repositories.FeedbackTypeOptionRepository;
import com.carsocialmedia.backend.feedback.internal.repositories.FeedbackVoteRepository;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 80;

    private final FeedbackRepository feedbackRepository;
    private final FeedbackTypeOptionRepository typeOptionRepository;
    private final FeedbackFeatureOptionRepository featureOptionRepository;
    private final FeedbackStatusOptionRepository statusOptionRepository;
    private final FeedbackVoteRepository voteRepository;
    private final FeedbackCommentRepository commentRepository;
    private final FeedbackSubscriptionRepository subscriptionRepository;
    private final ProfileService profileService;

    public FeedbackServiceImpl(FeedbackRepository feedbackRepository,
                               FeedbackTypeOptionRepository typeOptionRepository,
                               FeedbackFeatureOptionRepository featureOptionRepository,
                               FeedbackStatusOptionRepository statusOptionRepository,
                               FeedbackVoteRepository voteRepository,
                               FeedbackCommentRepository commentRepository,
                               FeedbackSubscriptionRepository subscriptionRepository,
                               ProfileService profileService) {
        this.feedbackRepository = feedbackRepository;
        this.typeOptionRepository = typeOptionRepository;
        this.featureOptionRepository = featureOptionRepository;
        this.statusOptionRepository = statusOptionRepository;
        this.voteRepository = voteRepository;
        this.commentRepository = commentRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.profileService = profileService;
    }

    @Override
    @Transactional
    public void submitFeedback(UUID userId, FeedbackRequest request) {
        if (!typeOptionRepository.existsById(request.type())) {
            throw new InvalidFeedbackTypeException(request.type());
        }
        // feature is optional; validate only when the client actually attached one.
        String feature = normalize(request.feature());
        if (feature != null && !featureOptionRepository.existsById(feature)) {
            throw new InvalidFeedbackFeatureException(feature);
        }

        FeedbackEntity feedback = new FeedbackEntity();
        feedback.setId(UUID.randomUUID());
        feedback.setUserId(userId);
        feedback.setContent(request.content());
        feedback.setType(request.type());
        feedback.setFeature(feature);
        feedback.setReproductionSteps(normalize(request.reproductionSteps()));
        feedbackRepository.save(feedback);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackTypeDto> listFeedbackTypes() {
        return typeOptionRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(option -> new FeedbackTypeDto(option.getId(), option.getType()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackFeatureDto> listFeedbackFeatures() {
        return featureOptionRepository.findAllByOrderByNameAsc().stream()
                .map(option -> new FeedbackFeatureDto(option.getId(), option.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyFeedbackDto> listMyFeedback(UUID userId) {
        List<FeedbackEntity> feedback = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // Resolve every referenced type/feature/status in one batch each, to avoid an N+1 lookup.
        Map<String, String> typeLabels = typeLabels();
        Map<String, String> featureLabels = featureLabels();
        Map<String, FeedbackStatusDto> statuses = statusesById();

        return feedback.stream()
                .map(entity -> new MyFeedbackDto(
                        entity.getId(),
                        entity.getContent(),
                        typeLabels.getOrDefault(entity.getType(), entity.getType()),
                        entity.getFeature() == null ? null
                                : featureLabels.getOrDefault(entity.getFeature(), entity.getFeature()),
                        entity.getReproductionSteps(),
                        entity.getResponse(),
                        resolveStatus(entity.getStatus(), statuses),
                        entity.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackStatusDto> listFeedbackStatuses() {
        return statusOptionRepository.findAll().stream()
                .sorted(Comparator.comparingInt(FeedbackStatusOptionEntity::getSortOrder))
                .map(option -> new FeedbackStatusDto(option.getId(), option.getName(), option.getColor()))
                .toList();
    }

    // ------------------------------------------------------------------
    // Public feedback board
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public FeedbackBoardPageDto getBoard(UUID viewerId, String sort, String type, String status,
                                         String cursor, int size) {
        BoardPage page = loadBoardPage(sort, type, status, cursor, size);
        return new FeedbackBoardPageDto(toBoardItems(page.rows(), viewerId), page.nextCursor());
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackBoardItemDto getBoardItem(UUID viewerId, UUID feedbackId) {
        FeedbackEntity feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new FeedbackNotFoundException(feedbackId));
        return toBoardItems(List.of(feedback), viewerId).getFirst();
    }

    @Override
    @Transactional
    public void vote(UUID userId, UUID feedbackId) {
        assertFeedbackExists(feedbackId);
        voteRepository.insertIgnoringConflict(feedbackId, userId);
    }

    @Override
    @Transactional
    public void unvote(UUID userId, UUID feedbackId) {
        voteRepository.deleteByIdFeedbackIdAndIdUserId(feedbackId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackCommentPageDto getComments(UUID viewerId, UUID feedbackId, String cursor, int size) {
        assertFeedbackExists(feedbackId);
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        PageRequest limit = PageRequest.of(0, pageSize + 1);

        List<FeedbackCommentEntity> rows;
        if (cursor == null) {
            rows = commentRepository.findByFeedbackIdOrderByCreatedAtDescIdDesc(feedbackId, limit);
        } else {
            BoardCursor decoded = BoardCursor.decode(cursor);
            rows = commentRepository.findPageAfter(feedbackId, decoded.instantKey(), decoded.id(), limit);
        }

        boolean hasMore = rows.size() > pageSize;
        List<FeedbackCommentEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        String nextCursor = hasMore
                ? BoardCursor.encode(page.getLast().getCreatedAt().toString(), page.getLast().getId())
                : null;

        Map<UUID, ProfileSearchResultDto> authors = profilesByIds(
                page.stream().map(FeedbackCommentEntity::getUserId).collect(Collectors.toSet()));

        List<FeedbackCommentDto> items = page.stream()
                .map(comment -> new FeedbackCommentDto(
                        comment.getId(),
                        authors.get(comment.getUserId()),
                        comment.getContent(),
                        comment.getUserId().equals(viewerId),
                        comment.getCreatedAt()))
                .toList();
        return new FeedbackCommentPageDto(items, nextCursor);
    }

    @Override
    @Transactional
    public FeedbackCommentDto addComment(UUID userId, UUID feedbackId, FeedbackCommentRequest request) {
        assertFeedbackExists(feedbackId);

        FeedbackCommentEntity comment = new FeedbackCommentEntity();
        comment.setId(UUID.randomUUID());
        comment.setFeedbackId(feedbackId);
        comment.setUserId(userId);
        comment.setContent(request.content().strip());
        commentRepository.save(comment);

        ProfileSearchResultDto author = profilesByIds(Set.of(userId)).get(userId);
        return new FeedbackCommentDto(comment.getId(), author, comment.getContent(), true, comment.getCreatedAt());
    }

    @Override
    @Transactional
    public void deleteComment(UUID userId, UUID feedbackId, UUID commentId) {
        FeedbackCommentEntity comment = commentRepository.findById(commentId)
                .filter(c -> c.getFeedbackId().equals(feedbackId))
                .orElseThrow(() -> new FeedbackCommentNotFoundException(commentId));
        if (!comment.getUserId().equals(userId)) {
            throw new NotFeedbackCommentAuthorException();
        }
        commentRepository.delete(comment);
    }

    @Override
    @Transactional
    public void subscribe(UUID userId, UUID feedbackId) {
        assertFeedbackExists(feedbackId);
        subscriptionRepository.insertIgnoringConflict(feedbackId, userId);
    }

    @Override
    @Transactional
    public void unsubscribe(UUID userId, UUID feedbackId) {
        subscriptionRepository.deleteByIdFeedbackIdAndIdUserId(feedbackId, userId);
    }

    // ------------------------------------------------------------------
    // Admin dashboard
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public AdminFeedbackPageDto listAllFeedback(String sort, String type, String status, String cursor, int size) {
        BoardPage page = loadBoardPage(sort, type, status, cursor, size);

        Map<String, String> typeLabels = typeLabels();
        Map<String, String> featureLabels = featureLabels();
        Map<String, FeedbackStatusDto> statuses = statusesById();
        Map<UUID, ProfileSearchResultDto> authors = profilesByIds(
                page.rows().stream().map(FeedbackEntity::getUserId).collect(Collectors.toSet()));

        List<AdminFeedbackDto> items = page.rows().stream()
                .map(f -> toAdminDto(f, typeLabels, featureLabels, statuses, authors.get(f.getUserId())))
                .toList();
        return new AdminFeedbackPageDto(items, page.nextCursor());
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackStatsDto getStats() {
        Map<String, Long> byType = feedbackRepository.countByType().stream()
                .collect(Collectors.toMap(FeedbackRepository.KeyCount::getKey, FeedbackRepository.KeyCount::getCnt));
        Map<String, Long> byStatus = feedbackRepository.countByStatus().stream()
                .collect(Collectors.toMap(FeedbackRepository.KeyCount::getKey, FeedbackRepository.KeyCount::getCnt));
        long total = byType.values().stream().mapToLong(Long::longValue).sum();
        long newThisWeek = feedbackRepository.countByCreatedAtAfter(Instant.now().minus(Duration.ofDays(7)));
        return new FeedbackStatsDto(total, newThisWeek, byType, byStatus);
    }

    @Override
    @Transactional
    public AdminFeedbackDto respond(UUID feedbackId, String response) {
        FeedbackEntity feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new FeedbackNotFoundException(feedbackId));
        feedback.setResponse(normalize(response));

        ProfileSearchResultDto author = profilesByIds(Set.of(feedback.getUserId())).get(feedback.getUserId());
        return toAdminDto(feedback, typeLabels(), featureLabels(), statusesById(), author);
    }

    @Override
    @Transactional
    public FeedbackStatusChangeDto updateStatus(UUID feedbackId, String statusId) {
        FeedbackEntity feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new FeedbackNotFoundException(feedbackId));
        FeedbackStatusOptionEntity status = statusOptionRepository.findById(statusId)
                .orElseThrow(() -> new InvalidFeedbackStatusException(statusId));

        feedback.setStatus(status.getId());

        // Author first, then subscribers, de-duplicated — the author is always notified.
        LinkedHashSet<UUID> recipients = new LinkedHashSet<>();
        recipients.add(feedback.getUserId());
        recipients.addAll(subscriptionRepository.findSubscriberIds(feedbackId));

        String preview = feedback.getContent().length() <= PREVIEW_LENGTH
                ? feedback.getContent()
                : feedback.getContent().substring(0, PREVIEW_LENGTH) + "…";

        return new FeedbackStatusChangeDto(
                feedbackId,
                preview,
                new FeedbackStatusDto(status.getId(), status.getName(), status.getColor()),
                List.copyOf(recipients));
    }

    // ------------------------------------------------------------------
    // Shared board plumbing
    // ------------------------------------------------------------------

    /** A page of feedback rows in final display order plus the encoded cursor for the next page. */
    private record BoardPage(List<FeedbackEntity> rows, String nextCursor) {}

    /**
     * Loads one board page for the given sort. Cursors are sort-specific: switching sorts restarts
     * pagination (the client passes {@code cursor = null} whenever the user changes tabs).
     */
    private BoardPage loadBoardPage(String sort, String type, String status, String cursor, int size) {
        int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        String effectiveSort = sort == null ? "top" : sort;

        return switch (effectiveSort) {
            case "new" -> loadNewPage(type, status, cursor, pageSize);
            case "trending" -> loadTrendingPage(type, status, cursor, pageSize);
            default -> loadTopPage(type, status, cursor, pageSize);
        };
    }

    private BoardPage loadNewPage(String type, String status, String cursor, int pageSize) {
        PageRequest limit = PageRequest.of(0, pageSize + 1);
        List<FeedbackEntity> rows;
        if (cursor == null) {
            rows = feedbackRepository.findBoardByNew(type, status, limit);
        } else {
            BoardCursor decoded = BoardCursor.decode(cursor);
            rows = feedbackRepository.findBoardByNewAfter(type, status, decoded.instantKey(), decoded.id(), limit);
        }

        boolean hasMore = rows.size() > pageSize;
        List<FeedbackEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore
                ? BoardCursor.encode(page.getLast().getCreatedAt().toString(), page.getLast().getId())
                : null;
        return new BoardPage(page, next);
    }

    private BoardPage loadTopPage(String type, String status, String cursor, int pageSize) {
        PageRequest limit = PageRequest.of(0, pageSize + 1);
        List<FeedbackEntity> rows;
        if (cursor == null) {
            rows = feedbackRepository.findBoardByTop(type, status, limit);
        } else {
            BoardCursor decoded = BoardCursor.decode(cursor);
            rows = feedbackRepository.findBoardByTopAfter(type, status, decoded.longKey(), decoded.id(), limit);
        }

        boolean hasMore = rows.size() > pageSize;
        List<FeedbackEntity> page = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore
                ? BoardCursor.encode(Long.toString(page.getLast().getVoteCount()), page.getLast().getId())
                : null;
        return new BoardPage(page, next);
    }

    private BoardPage loadTrendingPage(String type, String status, String cursor, int pageSize) {
        List<TrendingRow> rows;
        if (cursor == null) {
            rows = feedbackRepository.findBoardByTrending(type, status, pageSize + 1);
        } else {
            BoardCursor decoded = BoardCursor.decode(cursor);
            rows = feedbackRepository.findBoardByTrendingAfter(type, status, decoded.longKey(), decoded.id(),
                    pageSize + 1);
        }

        boolean hasMore = rows.size() > pageSize;
        List<TrendingRow> page = hasMore ? rows.subList(0, pageSize) : rows;
        String next = hasMore
                ? BoardCursor.encode(Long.toString(page.getLast().getRecent()), page.getLast().getId())
                : null;

        // The native query only returns ids in page order — load the entities and restore the order.
        List<UUID> ids = page.stream().map(TrendingRow::getId).toList();
        Map<UUID, FeedbackEntity> byId = feedbackRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(FeedbackEntity::getId, Function.identity()));
        List<FeedbackEntity> ordered = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        return new BoardPage(ordered, next);
    }

    /** Assembles board cards with one batch each for authors, viewer votes, and viewer subscriptions. */
    private List<FeedbackBoardItemDto> toBoardItems(List<FeedbackEntity> feedback, UUID viewerId) {
        if (feedback.isEmpty()) {
            return List.of();
        }

        Map<String, String> typeLabels = typeLabels();
        Map<String, String> featureLabels = featureLabels();
        Map<String, FeedbackStatusDto> statuses = statusesById();

        List<UUID> ids = feedback.stream().map(FeedbackEntity::getId).toList();
        Map<UUID, ProfileSearchResultDto> authors = profilesByIds(
                feedback.stream().map(FeedbackEntity::getUserId).collect(Collectors.toSet()));
        Set<UUID> voted = Set.copyOf(voteRepository.findVotedFeedbackIds(viewerId, ids));
        Set<UUID> subscribed = Set.copyOf(subscriptionRepository.findSubscribedFeedbackIds(viewerId, ids));

        return feedback.stream()
                .map(f -> new FeedbackBoardItemDto(
                        f.getId(),
                        f.getContent(),
                        typeLabels.getOrDefault(f.getType(), f.getType()),
                        f.getFeature() == null ? null : featureLabels.getOrDefault(f.getFeature(), f.getFeature()),
                        resolveStatus(f.getStatus(), statuses),
                        authors.get(f.getUserId()),
                        f.getVoteCount(),
                        f.getCommentCount(),
                        voted.contains(f.getId()),
                        subscribed.contains(f.getId()),
                        f.getResponse(),
                        f.getCreatedAt()))
                .toList();
    }

    private AdminFeedbackDto toAdminDto(FeedbackEntity f,
                                        Map<String, String> typeLabels,
                                        Map<String, String> featureLabels,
                                        Map<String, FeedbackStatusDto> statuses,
                                        ProfileSearchResultDto author) {
        return new AdminFeedbackDto(
                f.getId(),
                f.getContent(),
                typeLabels.getOrDefault(f.getType(), f.getType()),
                f.getFeature() == null ? null : featureLabels.getOrDefault(f.getFeature(), f.getFeature()),
                resolveStatus(f.getStatus(), statuses),
                author,
                f.getVoteCount(),
                f.getCommentCount(),
                f.getResponse(),
                f.getReproductionSteps(),
                f.getCreatedAt());
    }

    private void assertFeedbackExists(UUID feedbackId) {
        if (!feedbackRepository.existsById(feedbackId)) {
            throw new FeedbackNotFoundException(feedbackId);
        }
    }

    private Map<UUID, ProfileSearchResultDto> profilesByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return profileService.findByIds(ids).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));
    }

    private Map<String, String> typeLabels() {
        return typeOptionRepository.findAll().stream()
                .collect(Collectors.toMap(FeedbackTypeOptionEntity::getId, FeedbackTypeOptionEntity::getType));
    }

    private Map<String, String> featureLabels() {
        return featureOptionRepository.findAll().stream()
                .collect(Collectors.toMap(FeedbackFeatureOptionEntity::getId, FeedbackFeatureOptionEntity::getName));
    }

    private Map<String, FeedbackStatusDto> statusesById() {
        return statusOptionRepository.findAll().stream()
                .collect(Collectors.toMap(FeedbackStatusOptionEntity::getId,
                        option -> new FeedbackStatusDto(option.getId(), option.getName(), option.getColor())));
    }

    /**
     * Maps a stored status id to its resolved {@link FeedbackStatusDto}. {@code status} is NOT NULL in
     * the DB, but if an id somehow isn't in the options table we fall back to a label-less chip rather
     * than dropping the field.
     */
    private FeedbackStatusDto resolveStatus(String statusId, Map<String, FeedbackStatusDto> statuses) {
        if (statusId == null) {
            return null;
        }
        return statuses.getOrDefault(statusId, new FeedbackStatusDto(statusId, statusId, null));
    }

    /** Treats blank optional text as absent, so an empty string doesn't become a stored value. */
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
