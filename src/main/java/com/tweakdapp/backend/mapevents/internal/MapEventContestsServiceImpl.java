package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.mapevents.ContestEntryDecidedEvent;
import com.tweakdapp.backend.mapevents.ContestEntryRequestedEvent;
import com.tweakdapp.backend.mapevents.MapEventContestsService;
import com.tweakdapp.backend.mapevents.dto.CarEventHistoryEventDto;
import com.tweakdapp.backend.mapevents.dto.CarEventHistoryItemDto;
import com.tweakdapp.backend.mapevents.dto.CarEventPlacementDto;
import com.tweakdapp.backend.mapevents.dto.ContestCategoryDto;
import com.tweakdapp.backend.mapevents.dto.ContestDto;
import com.tweakdapp.backend.mapevents.dto.ContestEntryDto;
import com.tweakdapp.backend.mapevents.dto.ContestMyEntryDto;
import com.tweakdapp.backend.mapevents.dto.ContestPendingEntryDto;
import com.tweakdapp.backend.mapevents.dto.ContestViewerStateDto;
import com.tweakdapp.backend.mapevents.dto.request.CreateContestRequest;
import com.tweakdapp.backend.mapevents.dto.request.UpdateContestRequest;
import com.tweakdapp.backend.mapevents.exception.CarNotOwnedException;
import com.tweakdapp.backend.mapevents.exception.ContestClosedException;
import com.tweakdapp.backend.mapevents.exception.ContestEntryNotFoundException;
import com.tweakdapp.backend.mapevents.exception.ContestLimitException;
import com.tweakdapp.backend.mapevents.exception.ContestNotFoundException;
import com.tweakdapp.backend.mapevents.exception.ContestNotOpenException;
import com.tweakdapp.backend.mapevents.exception.HistoryCarNotFoundException;
import com.tweakdapp.backend.mapevents.exception.InvalidContestException;
import com.tweakdapp.backend.mapevents.exception.MapEventNotFoundException;
import com.tweakdapp.backend.mapevents.exception.NotEligibleToVoteException;
import com.tweakdapp.backend.mapevents.exception.NotEventOrganizerException;
import com.tweakdapp.backend.mapevents.internal.entities.ContestCategoryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryId;
import com.tweakdapp.backend.mapevents.internal.entities.ContestVoteEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestVoteId;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventAttendeeEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventAttendeeId;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventOrganizerEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventParticipantEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventParticipantId;
import com.tweakdapp.backend.mapevents.internal.realtime.ContestBoardPublisher;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestCategoryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestEntryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestVoteRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventAttendeeRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventOrganizerRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventParticipantRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRepository;
import com.tweakdapp.backend.profile.ProfileService;
import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;
import com.tweakdapp.backend.storage.StorageBucket;
import com.tweakdapp.backend.storage.StorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class MapEventContestsServiceImpl implements MapEventContestsService {

    /** Contests an event may run. A meet with more than this is not a meet, it is a spreadsheet. */
    static final int MAX_CONTESTS_PER_EVENT = 20;

    /** Accepted entries a ballot may hold — what keeps the DTO bounded and the vote sheet usable. */
    static final int MAX_ENTRIES_PER_CONTEST = 40;

    /** How far past the event's end the planned closing time may sit. */
    private static final Duration CLOSE_BOUND_AFTER_EVENT_END = Duration.ofHours(12);

    /** An open-ended event is treated as lasting this long, matching the map query's rule. */
    private static final Duration DEFAULT_EVENT_LENGTH = Duration.ofHours(24);

    private static final int HISTORY_CAP = 50;

    private final ContestRepository contestRepository;
    private final ContestCategoryRepository categoryRepository;
    private final ContestEntryRepository entryRepository;
    private final ContestVoteRepository voteRepository;
    private final MapEventRepository eventRepository;
    private final MapEventOrganizerRepository organizerRepository;
    private final MapEventAttendeeRepository attendeeRepository;
    private final MapEventParticipantRepository participantRepository;
    private final ProfileService profileService;
    private final GarageService garageService;
    private final StorageService storageService;
    private final ApplicationEventPublisher events;
    private final ContestFinalizer finalizer;
    private final ContestBoardPublisher boardPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    MapEventContestsServiceImpl(ContestRepository contestRepository,
                                ContestCategoryRepository categoryRepository,
                                ContestEntryRepository entryRepository,
                                ContestVoteRepository voteRepository,
                                MapEventRepository eventRepository,
                                MapEventOrganizerRepository organizerRepository,
                                MapEventAttendeeRepository attendeeRepository,
                                MapEventParticipantRepository participantRepository,
                                ProfileService profileService,
                                GarageService garageService,
                                StorageService storageService,
                                ApplicationEventPublisher events,
                                ContestFinalizer finalizer,
                                ContestBoardPublisher boardPublisher) {
        this.contestRepository = contestRepository;
        this.categoryRepository = categoryRepository;
        this.entryRepository = entryRepository;
        this.voteRepository = voteRepository;
        this.eventRepository = eventRepository;
        this.organizerRepository = organizerRepository;
        this.attendeeRepository = attendeeRepository;
        this.participantRepository = participantRepository;
        this.profileService = profileService;
        this.garageService = garageService;
        this.storageService = storageService;
        this.events = events;
        this.finalizer = finalizer;
        this.boardPublisher = boardPublisher;
    }

    // ==================================================================
    // Reads
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public List<ContestCategoryDto> listCategories() {
        return categoryRepository.findByAvailableTrueOrderBySortOrderAsc().stream()
                .map(MapEventContestsServiceImpl::toCategoryDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContestDto> listContests(UUID currentUserId, UUID eventId) {
        MapEventEntity event = loadVisibleEvent(currentUserId, eventId);
        boolean organizer = isOrganizer(eventId, currentUserId);
        return assembleAll(contestRepository.findByEventId(eventId), event, currentUserId, organizer);
    }

    @Override
    @Transactional(readOnly = true)
    public ContestDto getContest(UUID currentUserId, UUID eventId, UUID contestId) {
        MapEventEntity event = loadVisibleEvent(currentUserId, eventId);
        ContestEntity contest = loadContest(eventId, contestId);
        return assembleOne(contest, event, currentUserId);
    }

    // ==================================================================
    // Organizer: authoring
    // ==================================================================

    @Override
    @Transactional
    public ContestDto createContest(UUID currentUserId, UUID eventId, CreateContestRequest request) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);
        Instant now = Instant.now();
        requireEventNotFinished(event, now);

        ContestCategoryEntity category = categoryRepository.findById(request.categoryId().trim().toLowerCase())
                .filter(ContestCategoryEntity::isAvailable)
                .orElseThrow(() -> new InvalidContestException(
                        "Unknown or unavailable contest category: " + request.categoryId()));

        if (contestRepository.countByEventIdAndStatusNot(eventId, ContestEntity.STATUS_CANCELED)
                >= MAX_CONTESTS_PER_EVENT) {
            throw new ContestLimitException("This event already has " + MAX_CONTESTS_PER_EVENT + " contests");
        }

        validateWindow(event, request.opensAt(), request.closesAt());

        ContestEntity contest = new ContestEntity();
        contest.setId(UUID.randomUUID());
        contest.setEventId(eventId);
        contest.setCategory(category);
        contest.setTitle(request.title().trim());
        contest.setCriteria(normaliseCriteria(request.criteria()));
        // Always born locked: entries can come in, voting waits for the organizer to open it.
        contest.setStatus(ContestEntity.STATUS_SCHEDULED);
        contest.setOpensAt(request.opensAt());
        contest.setClosesAt(request.closesAt());
        contest.setCreatedBy(currentUserId);
        contest = contestRepository.saveAndFlush(contest);
        entityManager.refresh(contest);

        return assembleFresh(contest.getId(), event, currentUserId);
    }

    @Override
    @Transactional
    public ContestDto updateContest(UUID currentUserId, UUID eventId, UUID contestId, UpdateContestRequest request) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);
        ContestEntity contest = lockContest(eventId, contestId);
        Instant now = Instant.now();

        if (contest.isFinished()) {
            throw new ContestClosedException("A finished contest can no longer be edited");
        }
        requireEventNotFinished(event, now);

        if (contest.isOpen()) {
            if (request.title() != null || request.opensAt() != null) {
                throw new ContestClosedException(
                        "Only the closing time and the judging note can change once voting is open");
            }
        }

        if (request.title() != null) {
            contest.setTitle(request.title().trim());
        }
        if (request.criteria() != null) {
            contest.setCriteria(normaliseCriteria(request.criteria()));
        }

        if (request.opensAt() != null || request.closesAt() != null) {
            Instant opensAt = request.opensAt() != null ? request.opensAt() : contest.getOpensAt();
            Instant closesAt = request.closesAt() != null ? request.closesAt() : contest.getClosesAt();
            if (request.closesAt() != null && !request.closesAt().isAfter(now)) {
                throw new InvalidContestException("The closing time must be in the future");
            }
            validateWindow(event, opensAt, closesAt);
            contest.setOpensAt(opensAt);
            contest.setClosesAt(closesAt);
        }
        contestRepository.save(contest);

        if (request.closesAt() != null) {
            // The board shows the planned end, so a changed one has to reach the watchers.
            UUID id = contest.getId();
            String status = contest.getStatus();
            ContestFinalizer.afterCommit(() -> boardPublisher.publishStatus(eventId, id, status));
        }

        return assembleFresh(contestId, event, currentUserId);
    }

    @Override
    @Transactional
    public ContestDto openContest(UUID currentUserId, UUID eventId, UUID contestId) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);
        ContestEntity contest = lockContest(eventId, contestId);
        requireEventRunning(event, Instant.now());

        if (contest.isFinished()) {
            throw new ContestClosedException("This contest has finished; voting can't be reopened");
        }
        // Already open: idempotent, so a double tap is not an error.
        finalizer.open(contest, currentUserId);

        return assembleFresh(contestId, event, currentUserId);
    }

    @Override
    @Transactional
    public ContestDto finishContest(UUID currentUserId, UUID eventId, UUID contestId) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);
        ContestEntity contest = lockContest(eventId, contestId);

        if (contest.isScheduled()) {
            throw new ContestNotOpenException("Voting hasn't opened yet — open or delete the contest instead");
        }
        // Finished already: idempotent, hand back what there is.
        boolean awards = !MapEventEntity.STATUS_CANCELED.equals(event.getStatus());
        finalizer.finalizeLocked(contest, event, currentUserId, awards, Instant.now());

        return assembleFresh(contestId, event, currentUserId);
    }

    @Override
    @Transactional
    public void deleteContest(UUID currentUserId, UUID eventId, UUID contestId) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);
        ContestEntity contest = lockContest(eventId, contestId);

        if (!contest.isScheduled()) {
            throw new ContestClosedException("Only a contest that hasn't opened can be deleted; finish it instead");
        }
        contestRepository.delete(contest);
    }

    // ==================================================================
    // Owners: entries
    // ==================================================================

    @Override
    @Transactional
    public ContestDto requestEntry(UUID currentUserId, UUID eventId, UUID contestId, UUID carId) {
        MapEventEntity event = loadApprovedEvent(eventId);
        ContestEntity contest = lockContest(eventId, contestId);
        Instant now = Instant.now();
        requireEventRunning(event, now);
        if (contest.isFinished()) {
            throw new ContestClosedException("This contest has finished");
        }

        UUID owner = garageService.findCarOwnerIds(List.of(carId)).get(carId);
        if (owner == null || !owner.equals(currentUserId)) {
            throw new CarNotOwnedException(carId);
        }
        boolean acceptedInEvent = participantRepository.findById(new MapEventParticipantId(eventId, carId))
                .map(p -> MapEventParticipantEntity.ACCEPTED.equals(p.getStatus()))
                .orElse(false);
        if (!acceptedInEvent) {
            throw new InvalidContestException("Only a car accepted into this event's line-up can enter its contests");
        }

        ContestEntryId id = new ContestEntryId(contestId, carId);
        ContestEntryEntity entry = entryRepository.findById(id).orElse(null);
        boolean needsDecision;
        if (entry == null) {
            entry = new ContestEntryEntity();
            entry.setId(id);
            entry.setOwnerId(currentUserId);
            entry.setStatus(ContestEntryEntity.PENDING);
            needsDecision = true;
        } else if (entry.isLive()) {
            // Re-sending a pending or accepted entry is a no-op, not a new request.
            needsDecision = false;
        } else {
            entry.setStatus(ContestEntryEntity.PENDING);
            entry.setRejectionReason(null);
            entry.setDecidedAt(null);
            entry.setDecidedBy(null);
            needsDecision = true;
        }
        entryRepository.save(entry);

        if (needsDecision) {
            List<UUID> organizers = individualOrganizerIds(eventId).stream()
                    .filter(organizerId -> !organizerId.equals(currentUserId))
                    .toList();
            if (!organizers.isEmpty()) {
                events.publishEvent(new ContestEntryRequestedEvent(
                        eventId, event.getTitle(), contestId, contest.getTitle(), carId, currentUserId, organizers));
            }
        }

        return assembleFresh(contestId, event, currentUserId);
    }

    @Override
    @Transactional
    public ContestDto withdrawEntry(UUID currentUserId, UUID eventId, UUID contestId, UUID carId) {
        MapEventEntity event = loadVisibleEvent(currentUserId, eventId);
        ContestEntity contest = lockContest(eventId, contestId);

        ContestEntryEntity entry = entryRepository.findById(new ContestEntryId(contestId, carId))
                .orElseThrow(() -> new ContestEntryNotFoundException(carId));
        if (!entry.getOwnerId().equals(currentUserId)) {
            throw new CarNotOwnedException(carId);
        }

        if (entry.isPending()) {
            entryRepository.delete(entry);
        } else if (entry.isAccepted()) {
            if (!contest.isScheduled()) {
                throw new ContestClosedException("Entries are locked in once voting opens");
            }
            entry.setStatus(ContestEntryEntity.WITHDRAWN);
            entryRepository.save(entry);
        }
        // rejected / withdrawn: nothing to do.

        return assembleFresh(contestId, event, currentUserId);
    }

    // ==================================================================
    // Organizer: entry decisions
    // ==================================================================

    @Override
    @Transactional
    public ContestDto decideEntry(UUID currentUserId, UUID eventId, UUID contestId, UUID carId,
                                  String status, String reason) {
        MapEventEntity event = loadEvent(eventId);
        requireOrganizer(event, currentUserId);
        ContestEntity contest = lockContest(eventId, contestId);
        if (contest.isFinished()) {
            throw new ContestClosedException("This contest has finished; its ballot can no longer change");
        }

        String decision = status == null ? "" : status.trim().toLowerCase();
        if (!ContestEntryEntity.ACCEPTED.equals(decision) && !ContestEntryEntity.REJECTED.equals(decision)) {
            throw new InvalidContestException("A decision must be 'accepted' or 'rejected'");
        }
        String trimmedReason = (reason == null || reason.isBlank()) ? null : reason.trim();
        if (ContestEntryEntity.REJECTED.equals(decision) && trimmedReason == null) {
            throw new InvalidContestException("A rejection reason is required");
        }

        ContestEntryEntity entry = entryRepository.findById(new ContestEntryId(contestId, carId))
                .orElseThrow(() -> new ContestEntryNotFoundException(carId));

        boolean wasAccepted = entry.isAccepted();
        if (ContestEntryEntity.ACCEPTED.equals(decision)) {
            if (!wasAccepted && entryRepository.countByIdContestIdAndStatus(contestId, ContestEntryEntity.ACCEPTED)
                    >= MAX_ENTRIES_PER_CONTEST) {
                throw new ContestLimitException("This contest already has " + MAX_ENTRIES_PER_CONTEST + " cars");
            }
            entry.setStatus(ContestEntryEntity.ACCEPTED);
            entry.setRejectionReason(null);
        } else {
            entry.setStatus(ContestEntryEntity.REJECTED);
            entry.setRejectionReason(trimmedReason);
            if (wasAccepted) {
                // Off the ballot mid-vote: the people who voted for it get their vote back.
                voteRepository.deleteByContestIdAndCarId(contestId, carId);
            }
        }
        entry.setDecidedAt(Instant.now());
        entry.setDecidedBy(currentUserId);
        entryRepository.save(entry);

        if (!entry.getOwnerId().equals(currentUserId)) {
            events.publishEvent(new ContestEntryDecidedEvent(
                    eventId, event.getTitle(), contestId, contest.getTitle(), carId,
                    entry.isAccepted(), entry.getRejectionReason(), entry.getOwnerId()));
        }
        if (wasAccepted != entry.isAccepted()) {
            ContestFinalizer.afterCommit(() -> boardPublisher.markDirty(eventId, contestId));
        }

        return assembleFresh(contestId, event, currentUserId);
    }

    // ==================================================================
    // Attendees: voting
    // ==================================================================

    @Override
    @Transactional
    public ContestDto vote(UUID currentUserId, UUID eventId, UUID contestId, UUID carId) {
        MapEventEntity event = loadApprovedEvent(eventId);
        ContestEntity contest = lockContest(eventId, contestId);
        Instant now = Instant.now();

        if (event.hasFinished(now)) {
            throw new ContestClosedException("This event has finished; voting is closed");
        }
        if (contest.isFinished()) {
            throw new ContestClosedException("Voting has closed");
        }
        if (contest.isScheduled()) {
            // The planned window is a label; only an organizer opening the contest starts voting.
            throw new ContestNotOpenException("Voting hasn't opened yet");
        }

        if (!isEligibleToVote(currentUserId, eventId)) {
            throw new NotEligibleToVoteException(
                    "Only people attending the event — by RSVP or with a car in the line-up — can vote");
        }

        ContestEntryEntity entry = entryRepository.findById(new ContestEntryId(contestId, carId))
                .filter(ContestEntryEntity::isAccepted)
                .orElseThrow(() -> new ContestEntryNotFoundException(carId));
        if (entry.getOwnerId().equals(currentUserId)) {
            throw new NotEligibleToVoteException("You can't vote for your own car");
        }

        ContestVoteId id = new ContestVoteId(contestId, currentUserId);
        ContestVoteEntity vote = voteRepository.findById(id).orElse(null);
        boolean changed;
        if (vote == null) {
            vote = new ContestVoteEntity();
            vote.setId(id);
            vote.setCarId(carId);
            changed = true;
        } else {
            changed = !carId.equals(vote.getCarId());
            vote.setCarId(carId);
        }
        if (changed) {
            voteRepository.save(vote);
            ContestFinalizer.afterCommit(() -> boardPublisher.markDirty(eventId, contestId));
        }

        return assembleFresh(contestId, event, currentUserId);
    }

    /**
     * Who may cast a vote: anyone actually at the event. That is either an {@code attending} RSVP
     * or an owner with an accepted car in the line-up — registering a car never writes an
     * attendee row, and someone who brought a car is at the meet by definition.
     */
    private boolean isEligibleToVote(UUID userId, UUID eventId) {
        return hasAttendingRsvp(userId, eventId)
                || !participantRepository.findByIdEventIdAndOwnerIdAndStatus(
                        eventId, userId, MapEventParticipantEntity.ACCEPTED).isEmpty();
    }

    private boolean hasAttendingRsvp(UUID userId, UUID eventId) {
        return attendeeRepository.findById(new MapEventAttendeeId(userId, eventId))
                .map(a -> MapEventAttendeeEntity.ATTENDING.equals(a.getStatus()))
                .orElse(false);
    }

    // ==================================================================
    // Car history
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public List<CarEventHistoryItemDto> getCarHistory(UUID currentUserId, UUID carId) {
        // Every garage is public on this backend; existence is the only check.
        if (garageService.findCarsByIds(List.of(carId)).isEmpty()) {
            throw new HistoryCarNotFoundException(carId);
        }
        Instant now = Instant.now();

        List<MapEventParticipantEntity> participations =
                participantRepository.findByIdCarIdAndStatus(carId, MapEventParticipantEntity.ACCEPTED);
        if (participations.isEmpty()) {
            return List.of();
        }
        List<UUID> eventIds = participations.stream().map(p -> p.getId().getEventId()).toList();
        List<MapEventEntity> attended = eventRepository.findAllById(eventIds).stream()
                .filter(MapEventEntity::isApproved)
                .filter(e -> !MapEventEntity.STATUS_CANCELED.equals(e.getStatus()))
                .filter(e -> MapEventEntity.STATUS_LIVE.equals(e.getStatus()) || e.hasFinished(now))
                .sorted(Comparator.comparing(MapEventEntity::getStartsAt).reversed())
                .limit(HISTORY_CAP)
                .toList();
        if (attended.isEmpty()) {
            return List.of();
        }

        // Podium places, grouped by event.
        List<ContestEntryEntity> placed = entryRepository.findByIdCarId(carId).stream()
                .filter(e -> e.getFinalRank() != null && e.getFinalRank() <= ContestFinalizer.PODIUM_SIZE)
                .filter(e -> e.getFinalVotesCount() != null && e.getFinalVotesCount() >= 1)
                .toList();
        Map<UUID, ContestEntity> contests = placed.isEmpty() ? Map.of()
                : contestRepository.findAllWithCategoryByIdIn(placed.stream().map(e -> e.getId().getContestId()).toList())
                        .stream()
                        .filter(ContestEntity::isFinished)
                        .collect(Collectors.toMap(ContestEntity::getId, Function.identity()));
        Map<UUID, List<CarEventPlacementDto>> placementsByEvent = new HashMap<>();
        for (ContestEntryEntity entry : placed) {
            ContestEntity contest = contests.get(entry.getId().getContestId());
            if (contest == null) {
                continue;
            }
            placementsByEvent.computeIfAbsent(contest.getEventId(), k -> new ArrayList<>())
                    .add(new CarEventPlacementDto(
                            contest.getId(),
                            contest.getTitle(),
                            toCategoryDto(contest.getCategory()),
                            entry.getFinalRank(),
                            entry.getFinalVotesCount(),
                            contest.getVotesCount(),
                            contest.getFinishedAt()));
        }

        List<CarEventHistoryItemDto> items = new ArrayList<>(attended.size());
        for (MapEventEntity event : attended) {
            List<CarEventPlacementDto> placements = placementsByEvent.getOrDefault(event.getId(), List.of()).stream()
                    .sorted(Comparator.comparingInt(CarEventPlacementDto::finalRank))
                    .toList();
            String status = MapEventEntity.STATUS_LIVE.equals(event.getStatus())
                    ? MapEventEntity.STATUS_LIVE
                    : MapEventEntity.STATUS_PREVIOUS;
            items.add(new CarEventHistoryItemDto(
                    new CarEventHistoryEventDto(
                            event.getId(),
                            event.getTitle(),
                            resolveCoverUrl(event.getCoverImageKey()),
                            event.getLocationName(),
                            event.getStartsAt(),
                            event.getEndsAt(),
                            status),
                    MapEventParticipantEntity.ACCEPTED,
                    placements));
        }
        return items;
    }

    // ==================================================================
    // Assembly
    // ==================================================================

    /**
     * Re-reads a contest after a write so trigger-owned counts are fresh. The persistence context
     * is cleared first: the entries loaded before the vote landed would otherwise be handed back
     * with their stale counts.
     */
    private ContestDto assembleFresh(UUID contestId, MapEventEntity event, UUID viewerId) {
        entityManager.flush();
        entityManager.clear();
        ContestEntity contest = contestRepository.findByIdAndEventId(contestId, event.getId())
                .orElseThrow(() -> new ContestNotFoundException(contestId));
        MapEventEntity freshEvent = eventRepository.findById(event.getId()).orElse(event);
        return assembleOne(contest, freshEvent, viewerId);
    }

    private ContestDto assembleOne(ContestEntity contest, MapEventEntity event, UUID viewerId) {
        return assembleAll(List.of(contest), event, viewerId, isOrganizer(event.getId(), viewerId)).get(0);
    }

    /**
     * Builds the DTOs for several contests of one event with a fixed number of queries: entries
     * for all of them, the viewer's votes, the viewer's accepted event cars, one car batch, one
     * profile batch.
     */
    private List<ContestDto> assembleAll(List<ContestEntity> contests, MapEventEntity event,
                                         UUID viewerId, boolean viewerIsOrganizer) {
        if (contests.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        boolean eventOver = event.hasFinished(now);
        List<UUID> contestIds = contests.stream().map(ContestEntity::getId).toList();

        Map<UUID, List<ContestEntryEntity>> entriesByContest = entryRepository.findByIdContestIdIn(contestIds).stream()
                .collect(Collectors.groupingBy(e -> e.getId().getContestId()));

        Map<UUID, UUID> myVotes = voteRepository.findByIdContestIdInAndIdVoterId(contestIds, viewerId).stream()
                .collect(Collectors.toMap(v -> v.getId().getContestId(), ContestVoteEntity::getCarId));

        List<UUID> myAcceptedCars = participantRepository
                .findByIdEventIdAndOwnerIdAndStatus(event.getId(), viewerId, MapEventParticipantEntity.ACCEPTED)
                .stream().map(p -> p.getId().getCarId()).toList();

        // Bringing a car *is* attending, so an accepted entrant votes without a separate RSVP.
        boolean atTheEvent = !myAcceptedCars.isEmpty() || hasAttendingRsvp(viewerId, event.getId());

        List<UUID> carIds = entriesByContest.values().stream()
                .flatMap(List::stream)
                .filter(e -> e.isAccepted() || (viewerIsOrganizer && e.isPending()))
                .map(e -> e.getId().getCarId())
                .distinct()
                .toList();
        Map<UUID, CarSummaryDto> cars = resolveCars(carIds);

        Map<UUID, ProfileSearchResultDto> creators = resolveProfiles(
                contests.stream().map(ContestEntity::getCreatedBy).toList());

        List<ContestDto> out = new ArrayList<>(contests.size());
        for (ContestEntity contest : contests) {
            List<ContestEntryEntity> rows = entriesByContest.getOrDefault(contest.getId(), List.of());

            List<ContestEntryEntity> accepted = rows.stream().filter(ContestEntryEntity::isAccepted).toList();
            List<ContestEntryEntity> ranked = contest.isFinished()
                    ? accepted.stream()
                            .sorted(Comparator.comparing(ContestEntryEntity::getFinalRank,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList()
                    : ContestFinalizer.rank(accepted);

            List<ContestEntryDto> entries = new ArrayList<>(ranked.size());
            int rank = 1;
            for (ContestEntryEntity entry : ranked) {
                CarSummaryDto car = cars.get(entry.getId().getCarId());
                if (car == null) {
                    continue;
                }
                int votes = contest.isFinished() && entry.getFinalVotesCount() != null
                        ? entry.getFinalVotesCount() : entry.getVotesCount();
                entries.add(new ContestEntryDto(car, votes, rank++,
                        entry.getFinalRank() == null ? null : (int) entry.getFinalRank(),
                        entry.getLastVoteAt()));
            }

            List<ContestPendingEntryDto> pending = viewerIsOrganizer
                    ? rows.stream()
                            .filter(ContestEntryEntity::isPending)
                            .sorted(Comparator.comparing(ContestEntryEntity::getRequestedAt,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .map(e -> new ContestPendingEntryDto(cars.get(e.getId().getCarId()), e.getRequestedAt()))
                            .filter(dto -> dto.car() != null)
                            .toList()
                    : List.of();

            List<ContestMyEntryDto> myEntries = rows.stream()
                    .filter(e -> e.getOwnerId().equals(viewerId))
                    .map(e -> new ContestMyEntryDto(e.getId().getCarId(), e.getStatus(), e.getRejectionReason()))
                    .toList();
            boolean canEnter = !contest.isFinished() && !eventOver
                    && myAcceptedCars.stream().anyMatch(carId -> rows.stream()
                            .noneMatch(e -> e.getId().getCarId().equals(carId) && e.isLive()));

            boolean votingOpen = contest.isOpen() && !eventOver;

            ContestViewerStateDto viewer = new ContestViewerStateDto(
                    viewerIsOrganizer,
                    atTheEvent && votingOpen,
                    myVotes.get(contest.getId()),
                    canEnter,
                    myEntries);

            out.add(new ContestDto(
                    contest.getId(),
                    contest.getEventId(),
                    toCategoryDto(contest.getCategory()),
                    contest.getTitle(),
                    contest.getCriteria(),
                    contest.getStatus(),
                    contest.getOpensAt(),
                    contest.getClosesAt(),
                    contest.getFinishedAt(),
                    contest.isFinishedEarly(),
                    contest.getVotesCount(),
                    contest.getEntriesCount(),
                    entries,
                    viewer,
                    pending,
                    creators.get(contest.getCreatedBy()),
                    contest.getCreatedAt()));
        }
        return out;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private MapEventEntity loadEvent(UUID eventId) {
        return eventRepository.findWithCategoryById(eventId)
                .orElseThrow(() -> new MapEventNotFoundException(eventId));
    }

    private MapEventEntity loadApprovedEvent(UUID eventId) {
        MapEventEntity event = loadEvent(eventId);
        if (!event.isApproved()) {
            throw new MapEventNotFoundException(eventId);
        }
        return event;
    }

    private MapEventEntity loadVisibleEvent(UUID currentUserId, UUID eventId) {
        MapEventEntity event = loadEvent(eventId);
        if (!event.isApproved() && !isOrganizer(eventId, currentUserId)) {
            throw new MapEventNotFoundException(eventId);
        }
        return event;
    }

    private ContestEntity loadContest(UUID eventId, UUID contestId) {
        return contestRepository.findByIdAndEventId(contestId, eventId)
                .orElseThrow(() -> new ContestNotFoundException(contestId));
    }

    private ContestEntity lockContest(UUID eventId, UUID contestId) {
        ContestEntity contest = contestRepository.lockById(contestId)
                .orElseThrow(() -> new ContestNotFoundException(contestId));
        if (!contest.getEventId().equals(eventId)) {
            throw new ContestNotFoundException(contestId);
        }
        return contest;
    }

    private boolean isOrganizer(UUID eventId, UUID userId) {
        return userId != null && organizerRepository.existsByEventIdAndIndividualOrganizerId(eventId, userId);
    }

    private void requireOrganizer(MapEventEntity event, UUID userId) {
        if (!isOrganizer(event.getId(), userId)) {
            throw new NotEventOrganizerException("You do not organize this event");
        }
    }

    private List<UUID> individualOrganizerIds(UUID eventId) {
        return organizerRepository.findByEventIdOrderByRoleAscCreatedAtAsc(eventId).stream()
                .map(MapEventOrganizerEntity::getIndividualOrganizerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * For everything the public takes part in — opening voting, entering a car. An event that
     * staff have not approved is not on the map, so nothing may start running inside it.
     */
    private static void requireEventRunning(MapEventEntity event, Instant now) {
        if (!event.isApproved()) {
            throw new ContestClosedException("Contests can only be run inside an approved event");
        }
        requireEventNotFinished(event, now);
    }

    /**
     * For the organizer's own authoring calls — create and update. Deliberately weaker than
     * {@link #requireEventRunning}: the create-event wizard lets an organizer line up an event's
     * contests in the same sitting they submit the event, and that event is born {@code pending}.
     * Those contests are born {@code scheduled} and stay inert — {@link #openContest} and
     * {@link #requestEntry} still demand an approved event — so authoring them early exposes
     * nothing to anyone but the organizer, who can already read them (see {@code loadVisibleEvent}).
     */
    private static void requireEventNotFinished(MapEventEntity event, Instant now) {
        if (event.hasFinished(now)) {
            throw new ContestClosedException("This event has finished");
        }
    }

    /**
     * The planned window is shown to attendees, never enforced: an organizer opens and finishes
     * voting by hand. It still has to read sensibly — close after it opens, and no later than 12
     * hours after the event ends.
     */
    private static void validateWindow(MapEventEntity event, Instant opensAt, Instant closesAt) {
        if (!closesAt.isAfter(opensAt)) {
            throw new InvalidContestException("Voting must close after it opens");
        }
        Instant eventEnd = event.getEndsAt() != null
                ? event.getEndsAt()
                : event.getStartsAt().plus(DEFAULT_EVENT_LENGTH);
        Instant bound = eventEnd.plus(CLOSE_BOUND_AFTER_EVENT_END);
        if (closesAt.isAfter(bound)) {
            throw new InvalidContestException("Voting can't close more than 12 hours after the event ends");
        }
    }

    private static String normaliseCriteria(String criteria) {
        if (criteria == null) {
            return null;
        }
        String trimmed = criteria.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ContestCategoryDto toCategoryDto(ContestCategoryEntity category) {
        return new ContestCategoryDto(category.getId(), category.getLabel(), category.getIcon());
    }

    private Map<UUID, CarSummaryDto> resolveCars(List<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return garageService.findCarsByIds(distinct).stream()
                .collect(Collectors.toMap(CarSummaryDto::id, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, ProfileSearchResultDto> resolveProfiles(List<UUID> ids) {
        List<UUID> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return profileService.findByIds(distinct).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity(), (a, b) -> a));
    }

    private String resolveCoverUrl(String storedKey) {
        if (storedKey == null || storedKey.isBlank() || storedKey.startsWith("http")) {
            return storedKey;
        }
        return storageService.publicUrl(StorageBucket.MAP_EVENTS, storedKey);
    }

    /** Package-visible for the sibling service: a car that left the event leaves its ballots. */
    void removeEntriesForCars(UUID eventId, List<UUID> carIds) {
        if (carIds.isEmpty()) {
            return;
        }
        entryRepository.deleteByEventIdAndCarIds(eventId, carIds);
    }

}
