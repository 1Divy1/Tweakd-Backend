package com.carsocialmedia.backend.feedbackfeed.internal;

import com.carsocialmedia.backend.feedbackfeed.FeedbackFeedService;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackAuthorDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackCategoryDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackFeedPageDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackFeedStatusDto;
import com.carsocialmedia.backend.feedbackfeed.dto.FeedbackMessageDto;
import com.carsocialmedia.backend.feedbackfeed.dto.request.SubmitFeedbackMessageRequest;
import com.carsocialmedia.backend.feedbackfeed.exception.FeedbackDeletionClosedException;
import com.carsocialmedia.backend.feedbackfeed.exception.FeedbackMessageNotFoundException;
import com.carsocialmedia.backend.feedbackfeed.exception.FeedbackVotingClosedException;
import com.carsocialmedia.backend.feedbackfeed.exception.InvalidFeedbackFeedRequestException;
import com.carsocialmedia.backend.feedbackfeed.exception.NotFeedbackAuthorException;
import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedMessageEntity;
import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedVoteEntity;
import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedVoteId;
import com.carsocialmedia.backend.feedbackfeed.internal.repositories.FeedbackFeedMessageRepository;
import com.carsocialmedia.backend.feedbackfeed.internal.repositories.FeedbackFeedStatusOptionRepository;
import com.carsocialmedia.backend.feedbackfeed.internal.repositories.FeedbackFeedTypeOptionRepository;
import com.carsocialmedia.backend.feedbackfeed.internal.repositories.FeedbackFeedVoteRepository;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The community feedback feed.
 *
 * <p>Three things in the database do work this class deliberately does not repeat: the vote counts
 * on a message are trigger-maintained, the author's status-change notification is written by a
 * trigger, and {@code completed_at} is stamped by a trigger. Every write therefore ends in
 * {@link #reloadAndAssemble}, which flushes and re-reads the row so the response carries what the
 * database actually holds rather than the stale copy in the session.
 */
@Service
class FeedbackFeedServiceImpl implements FeedbackFeedService {

    /** Roadmap order. The reference table has no sort column, so the order lives here. */
    private static final List<String> STATUS_ORDER = List.of(
            FeedbackFeedMessageEntity.STATUS_SENT,
            FeedbackFeedMessageEntity.STATUS_UNDER_DEVELOPMENT,
            FeedbackFeedMessageEntity.STATUS_COMPLETED);

    private static final String SORT_NEWEST = "newest";
    private static final String SORT_POPULAR = "popular";
    private static final String SORT_OLDEST = "oldest";

    private static final int MAX_PAGE_SIZE = 50;

    private final FeedbackFeedMessageRepository messageRepository;
    private final FeedbackFeedVoteRepository voteRepository;
    private final FeedbackFeedTypeOptionRepository typeRepository;
    private final FeedbackFeedStatusOptionRepository statusRepository;
    private final ProfileService profileService;
    private final EntityManager entityManager;

    FeedbackFeedServiceImpl(FeedbackFeedMessageRepository messageRepository,
                            FeedbackFeedVoteRepository voteRepository,
                            FeedbackFeedTypeOptionRepository typeRepository,
                            FeedbackFeedStatusOptionRepository statusRepository,
                            ProfileService profileService,
                            EntityManager entityManager) {
        this.messageRepository = messageRepository;
        this.voteRepository = voteRepository;
        this.typeRepository = typeRepository;
        this.statusRepository = statusRepository;
        this.profileService = profileService;
        this.entityManager = entityManager;
    }

    // ==================================================================
    // Reference data
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackCategoryDto> listTypes() {
        return typeRepository.findAll().stream()
                .map(row -> new FeedbackCategoryDto(row.getId(), row.getType()))
                .sorted(Comparator.comparing(FeedbackCategoryDto::label))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackFeedStatusDto> listStatuses() {
        return statusRepository.findAll().stream()
                .map(row -> new FeedbackFeedStatusDto(row.getId(), row.getStatus()))
                .sorted(Comparator.comparingInt(dto -> roadmapPosition(dto.id())))
                .toList();
    }

    // ==================================================================
    // Reading the feed
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public FeedbackFeedPageDto getFeed(UUID currentUserId, String sort, String cursor, int size) {
        String normalisedSort = normaliseSort(sort);
        int pageSize = clampSize(size);
        boolean scored = SORT_POPULAR.equals(normalisedSort);
        FeedbackFeedCursor from = FeedbackFeedCursor.decode(cursor, scored);
        boolean firstPage = from == null;

        List<FeedbackFeedMessageEntity> rows = switch (normalisedSort) {
            case SORT_POPULAR -> messageRepository.findMostVoted(
                    firstPage, firstPage ? null : from.score(), firstPage ? null : from.id(), pageSize + 1);
            case SORT_OLDEST -> messageRepository.findOldest(
                    firstPage, firstPage ? null : from.timestamp(), firstPage ? null : from.id(), pageSize + 1);
            default -> messageRepository.findNewest(
                    firstPage, firstPage ? null : from.timestamp(), firstPage ? null : from.id(), pageSize + 1);
        };

        return toPage(rows, pageSize, currentUserId, last -> scored
                ? FeedbackFeedCursor.byScore(last.getNetVotes(), last.getId())
                : FeedbackFeedCursor.byTime(last.getCreatedAt(), last.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackFeedPageDto getCompleted(UUID currentUserId, String cursor, int size) {
        int pageSize = clampSize(size);
        FeedbackFeedCursor from = FeedbackFeedCursor.decode(cursor, false);
        boolean firstPage = from == null;

        List<FeedbackFeedMessageEntity> rows = messageRepository.findCompleted(
                firstPage, firstPage ? null : from.timestamp(), firstPage ? null : from.id(), pageSize + 1);

        return toPage(rows, pageSize, currentUserId,
                last -> FeedbackFeedCursor.byTime(last.getCompletedAt(), last.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackMessageDto getMessage(UUID currentUserId, UUID messageId) {
        return assemble(List.of(loadVisible(messageId)), currentUserId).getFirst();
    }

    // ==================================================================
    // Authoring
    // ==================================================================

    @Override
    @Transactional
    public FeedbackMessageDto submit(UUID currentUserId, SubmitFeedbackMessageRequest request) {
        if (!typeRepository.existsById(request.type())) {
            throw new InvalidFeedbackFeedRequestException("Unknown feedback category: " + request.type());
        }

        FeedbackFeedMessageEntity message = new FeedbackFeedMessageEntity();
        message.setId(UUID.randomUUID());
        message.setAuthorId(currentUserId);
        message.setType(request.type());
        message.setMessage(request.message().trim());
        messageRepository.save(message);

        // status, created_at and the vote counters are all DB defaults, so the row has to come back
        // from Postgres before it can be rendered.
        return reloadAndAssemble(message.getId(), currentUserId);
    }

    @Override
    @Transactional
    public void deleteOwn(UUID currentUserId, UUID messageId) {
        FeedbackFeedMessageEntity message = loadVisible(messageId);
        if (!message.getAuthorId().equals(currentUserId)) {
            throw new NotFeedbackAuthorException();
        }
        // Only while the request is still untouched. Once staff move it to under_development or
        // completed it is a public roadmap entry that other people have voted on, and a hard
        // delete would take that history with it.
        if (!FeedbackFeedMessageEntity.STATUS_SENT.equals(message.getStatus())) {
            throw new FeedbackDeletionClosedException();
        }
        // An author's own delete is a hard delete — the votes go with it via the FK cascade.
        // is_deleted is reserved for staff removals, which have to survive for audit.
        messageRepository.delete(message);
    }

    // ==================================================================
    // Voting
    // ==================================================================

    @Override
    @Transactional
    public FeedbackMessageDto vote(UUID currentUserId, UUID messageId, int value) {
        short direction = normaliseVote(value);
        FeedbackFeedMessageEntity message = loadVotable(messageId);

        FeedbackFeedVoteId id = new FeedbackFeedVoteId(currentUserId, message.getId());
        Optional<FeedbackFeedVoteEntity> existing = voteRepository.findById(id);

        if (existing.isEmpty()) {
            FeedbackFeedVoteEntity vote = new FeedbackFeedVoteEntity();
            vote.setId(id);
            vote.setVoteType(direction);
            // persist, not save: the id is assigned, so save() would merge and cost a redundant
            // select on a row we have just established does not exist.
            entityManager.persist(vote);
        } else if (existing.get().getVoteType() == direction) {
            // Same direction again — the client tapped an active arrow, which withdraws the vote.
            voteRepository.delete(existing.get());
        } else {
            FeedbackFeedVoteEntity vote = existing.get();
            vote.setVoteType(direction);
            vote.setUpdatedAt(Instant.now());
        }

        return reloadAndAssemble(message.getId(), currentUserId);
    }

    @Override
    @Transactional
    public FeedbackMessageDto removeVote(UUID currentUserId, UUID messageId) {
        FeedbackFeedMessageEntity message = loadVotable(messageId);
        voteRepository.deleteById(new FeedbackFeedVoteId(currentUserId, message.getId()));
        return reloadAndAssemble(message.getId(), currentUserId);
    }

    // ==================================================================
    // Admin
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public FeedbackFeedPageDto listAll(String type, String status, boolean includeRemoved, String cursor, int size) {
        int pageSize = clampSize(size);
        FeedbackFeedCursor from = FeedbackFeedCursor.decode(cursor, false);
        boolean firstPage = from == null;

        List<FeedbackFeedMessageEntity> rows = messageRepository.findForAdmin(
                blankToNull(type), blankToNull(status), includeRemoved,
                firstPage, firstPage ? null : from.timestamp(), firstPage ? null : from.id(), pageSize + 1);

        // No viewer: staff accounts have no profile row, so there is no vote of theirs to resolve.
        return toPage(rows, pageSize, null,
                last -> FeedbackFeedCursor.byTime(last.getCreatedAt(), last.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackMessageDto> listTopVoted(int limit) {
        List<FeedbackFeedMessageEntity> rows =
                messageRepository.findMostVoted(true, null, null, clampSize(limit));
        return assemble(rows, null);
    }

    @Override
    @Transactional
    public FeedbackMessageDto updateStatus(UUID messageId, String statusId) {
        if (!statusRepository.existsById(statusId)) {
            throw new InvalidFeedbackFeedRequestException("Unknown feedback status: " + statusId);
        }
        FeedbackFeedMessageEntity message = loadVisible(messageId);
        message.setStatus(statusId);

        // The UPDATE fires two triggers: one notifies the author, one stamps or clears
        // completed_at. Both are invisible to the session, hence the reload.
        return reloadAndAssemble(message.getId(), null);
    }

    @Override
    @Transactional
    public FeedbackMessageDto respond(UUID messageId, String response) {
        FeedbackFeedMessageEntity message = loadVisible(messageId);
        message.setStaffResponseMessage(blankToNull(response));
        return reloadAndAssemble(message.getId(), null);
    }

    @Override
    @Transactional
    public void removeAsStaff(UUID messageId) {
        // findById, not loadVisible: removing an already-removed message is a no-op, not a 404.
        messageRepository.findById(messageId)
                .orElseThrow(() -> new FeedbackMessageNotFoundException(messageId))
                .setDeleted(true);
    }

    // ==================================================================
    // Assembly
    // ==================================================================

    private FeedbackFeedPageDto toPage(List<FeedbackFeedMessageEntity> rows,
                                       int pageSize,
                                       UUID viewerId,
                                       Function<FeedbackFeedMessageEntity, FeedbackFeedCursor> cursorOf) {
        boolean hasMore = rows.size() > pageSize;
        List<FeedbackFeedMessageEntity> page = hasMore ? rows.subList(0, pageSize) : rows;

        String nextCursor = hasMore ? cursorOf.apply(page.getLast()).encode() : null;
        return new FeedbackFeedPageDto(assemble(page, viewerId), nextCursor);
    }

    /**
     * Turns rows into cards, resolving authors, the viewer's own votes and the reference labels in
     * one batch each rather than per row.
     */
    private List<FeedbackMessageDto> assemble(List<FeedbackFeedMessageEntity> messages, UUID viewerId) {
        if (messages.isEmpty()) {
            return List.of();
        }

        Map<UUID, FeedbackAuthorDto> authors = resolveAuthors(messages);
        Map<UUID, Short> viewerVotes = resolveViewerVotes(messages, viewerId);
        Map<String, FeedbackCategoryDto> types = indexBy(listTypes(), FeedbackCategoryDto::id);
        Map<String, FeedbackFeedStatusDto> statuses = indexBy(listStatuses(), FeedbackFeedStatusDto::id);

        return messages.stream()
                .map(message -> {
                    Short own = viewerVotes.get(message.getId());
                    return new FeedbackMessageDto(
                            message.getId(),
                            authors.get(message.getAuthorId()),
                            message.getMessage(),
                            types.getOrDefault(message.getType(),
                                    new FeedbackCategoryDto(message.getType(), message.getType())),
                            statuses.getOrDefault(message.getStatus(),
                                    new FeedbackFeedStatusDto(message.getStatus(), message.getStatus())),
                            message.getStaffResponseMessage(),
                            message.getUpVotes(),
                            message.getDownVotes(),
                            message.getNetVotes(),
                            own == null ? null : own.intValue(),
                            viewerId != null && viewerId.equals(message.getAuthorId()),
                            message.isDeleted(),
                            message.getCreatedAt(),
                            message.getCompletedAt());
                })
                .toList();
    }

    private Map<UUID, FeedbackAuthorDto> resolveAuthors(List<FeedbackFeedMessageEntity> messages) {
        List<UUID> ids = messages.stream().map(FeedbackFeedMessageEntity::getAuthorId).distinct().toList();
        Map<UUID, FeedbackAuthorDto> byId = new LinkedHashMap<>();
        for (ProfileSearchResultDto profile : profileService.findByIds(ids)) {
            byId.put(profile.id(), new FeedbackAuthorDto(
                    profile.id(), profile.name(), profile.username(), profile.avatarUrl()));
        }
        return byId;
    }

    private Map<UUID, Short> resolveViewerVotes(List<FeedbackFeedMessageEntity> messages, UUID viewerId) {
        if (viewerId == null) {
            return Map.of();
        }
        List<UUID> ids = messages.stream().map(FeedbackFeedMessageEntity::getId).toList();
        Map<UUID, Short> byMessage = new LinkedHashMap<>();
        for (FeedbackFeedVoteEntity vote : voteRepository.findByIdUserIdAndIdMessageIdIn(viewerId, ids)) {
            byMessage.put(vote.getId().getMessageId(), vote.getVoteType());
        }
        return byMessage;
    }

    private FeedbackMessageDto reloadAndAssemble(UUID messageId, UUID viewerId) {
        entityManager.flush();
        FeedbackFeedMessageEntity message = messageRepository.findById(messageId)
                .orElseThrow(() -> new FeedbackMessageNotFoundException(messageId));
        entityManager.refresh(message);
        return assemble(List.of(message), viewerId).getFirst();
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private FeedbackFeedMessageEntity loadVisible(UUID messageId) {
        return messageRepository.findByIdAndDeletedFalse(messageId)
                .orElseThrow(() -> new FeedbackMessageNotFoundException(messageId));
    }

    /**
     * Loads a message that can still be voted on. A trigger rejects vote writes against completed
     * messages outright; catching that here keeps the failure a clean 409 instead of a SQL error
     * that would already have marked the transaction rollback-only.
     */
    private FeedbackFeedMessageEntity loadVotable(UUID messageId) {
        FeedbackFeedMessageEntity message = loadVisible(messageId);
        if (message.isCompleted()) {
            throw new FeedbackVotingClosedException();
        }
        return message;
    }

    private short normaliseVote(int value) {
        if (value != FeedbackFeedVoteEntity.UP && value != FeedbackFeedVoteEntity.DOWN) {
            throw new InvalidFeedbackFeedRequestException("A vote must be 1 (up) or -1 (down)");
        }
        return (short) value;
    }

    private String normaliseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return SORT_NEWEST;
        }
        String normalised = sort.trim().toLowerCase();
        return switch (normalised) {
            case SORT_NEWEST, SORT_POPULAR, SORT_OLDEST -> normalised;
            default -> throw new InvalidFeedbackFeedRequestException(
                    "Unknown sort: " + sort + " (expected newest, popular or oldest)");
        };
    }

    private static int clampSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }

    private static int roadmapPosition(String statusId) {
        int index = STATUS_ORDER.indexOf(statusId);
        return index < 0 ? STATUS_ORDER.size() : index;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> Map<String, T> indexBy(List<T> values, Function<T, String> key) {
        Map<String, T> byKey = new LinkedHashMap<>();
        values.forEach(value -> byKey.put(key.apply(value), value));
        return byKey;
    }
}
