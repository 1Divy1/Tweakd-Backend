package com.tweakdapp.backend.mapevents.internal;

import com.tweakdapp.backend.badges.BadgeService;
import com.tweakdapp.backend.badges.BadgeTrigger;
import com.tweakdapp.backend.garage.GarageService;
import com.tweakdapp.backend.garage.dto.CarSummaryDto;
import com.tweakdapp.backend.mapevents.ContestFinishedEvent;
import com.tweakdapp.backend.mapevents.ContestOpenedEvent;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventAttendeeEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventEntity;
import com.tweakdapp.backend.mapevents.internal.entities.MapEventParticipantEntity;
import com.tweakdapp.backend.mapevents.internal.realtime.ContestBoardPublisher;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestEntryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventAttendeeRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventParticipantRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.MapEventRepository;
import com.tweakdapp.backend.reputation.ReputationReasons;
import com.tweakdapp.backend.reputation.ReputationService;
import com.tweakdapp.backend.reputation.ReputationSourceType;
import com.tweakdapp.backend.reputation.dto.ReputationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The two contest transitions that have consequences beyond the row: <strong>opening</strong>
 * (tell the meet) and <strong>finalising</strong> (freeze standings, pay the podium, tell the
 * meet). Both are driven by an organizer — nothing here happens on a timer — and both are
 * idempotent under the contest's row lock, so two organizers tapping at once, or a tap racing
 * the event being marked finished, still does the work exactly once.
 *
 * <p>Awards happen inside the same transaction as the status change, so a rollback takes them
 * back; both {@code ReputationService.award} and {@code BadgeService.awardForTrigger} are
 * exactly-once by database index, so no guard is needed here. Notifications and the realtime
 * pushes are after-commit.
 */
@Component
public class ContestFinalizer {

    private static final Logger log = LoggerFactory.getLogger(ContestFinalizer.class);

    /** Places that earn something. Rank ≤ this, with at least one vote. */
    static final int PODIUM_SIZE = 3;

    private final ContestRepository contestRepository;
    private final ContestEntryRepository entryRepository;
    private final MapEventRepository eventRepository;
    private final MapEventAttendeeRepository attendeeRepository;
    private final MapEventParticipantRepository participantRepository;
    private final GarageService garageService;
    private final ReputationService reputationService;
    private final BadgeService badgeService;
    private final ApplicationEventPublisher events;
    private final ContestBoardPublisher boardPublisher;

    ContestFinalizer(ContestRepository contestRepository,
                     ContestEntryRepository entryRepository,
                     MapEventRepository eventRepository,
                     MapEventAttendeeRepository attendeeRepository,
                     MapEventParticipantRepository participantRepository,
                     GarageService garageService,
                     ReputationService reputationService,
                     BadgeService badgeService,
                     ApplicationEventPublisher events,
                     ContestBoardPublisher boardPublisher) {
        this.contestRepository = contestRepository;
        this.entryRepository = entryRepository;
        this.eventRepository = eventRepository;
        this.attendeeRepository = attendeeRepository;
        this.participantRepository = participantRepository;
        this.garageService = garageService;
        this.reputationService = reputationService;
        this.badgeService = badgeService;
        this.events = events;
        this.boardPublisher = boardPublisher;
    }

    // ---- opening ------------------------------------------------------------

    /**
     * Moves a scheduled contest to {@code open}, if it still is scheduled. Returns whether this
     * call did it. Requires the caller to hold the row lock.
     */
    public boolean open(ContestEntity contest, UUID actorId) {
        if (!contest.isScheduled()) {
            return false;
        }
        contest.setStatus(ContestEntity.STATUS_OPEN);
        contestRepository.save(contest);
        announceOpened(contest, actorId);
        return true;
    }

    private void announceOpened(ContestEntity contest, UUID actorId) {
        MapEventEntity event = eventRepository.findById(contest.getEventId()).orElse(null);
        String eventTitle = event == null ? "" : event.getTitle();
        List<UUID> audience = audience(contest, Set.of());
        if (actorId != null) {
            audience = audience.stream().filter(id -> !id.equals(actorId)).toList();
        }
        if (!audience.isEmpty()) {
            events.publishEvent(new ContestOpenedEvent(
                    contest.getEventId(), eventTitle, contest.getId(), contest.getTitle(), audience));
        }
        afterCommit(() -> boardPublisher.publishStatus(
                contest.getEventId(), contest.getId(), ContestEntity.STATUS_OPEN));
    }

    // ---- finalising ---------------------------------------------------------

    /**
     * Finalises an open contest. The caller must already hold the row lock. Returns whether this
     * call did the work (false when it was not open).
     *
     * @param finishedBy the organizer who closed it, or {@code null} when the event's own
     *                   finish/cancel cascade did
     * @param awards     whether the podium is paid (false when the event was cancelled)
     */
    public boolean finalizeLocked(ContestEntity contest, MapEventEntity event, UUID finishedBy,
                                  boolean awards, Instant now) {
        if (!contest.isOpen()) {
            return false;
        }

        List<ContestEntryEntity> ranked = rank(
                entryRepository.findByIdContestIdAndStatus(contest.getId(), ContestEntryEntity.ACCEPTED));

        short rank = 1;
        for (ContestEntryEntity entry : ranked) {
            entry.setFinalRank(rank++);
            entry.setFinalVotesCount(entry.getVotesCount());
        }
        entryRepository.saveAll(ranked);

        contest.setStatus(ContestEntity.STATUS_FINISHED);
        contest.setFinishedAt(now);
        contest.setFinishedEarly(now.isBefore(contest.getClosesAt()));
        contest.setFinishedBy(finishedBy);
        contestRepository.save(contest);

        List<ContestEntryEntity> podium = ranked.stream()
                .filter(e -> e.getFinalRank() <= PODIUM_SIZE && e.getVotesCount() >= 1)
                .toList();

        if (awards && !podium.isEmpty()) {
            award(contest, event, podium, now);
            announceFinished(contest, event, podium);
        }

        afterCommit(() -> boardPublisher.publishStatus(
                contest.getEventId(), contest.getId(), ContestEntity.STATUS_FINISHED));
        return true;
    }

    /**
     * Closes every contest of an event that is still open — what marking the event finished or
     * cancelling it does, inside that same transaction. Scheduled contests that never opened are
     * left alone; they simply never will.
     */
    public void finalizeAllOpen(MapEventEntity event, UUID actorId, boolean awards) {
        Instant now = Instant.now();
        for (UUID contestId : contestRepository.findIdsByEventIdAndStatus(event.getId(), ContestEntity.STATUS_OPEN)) {
            contestRepository.lockById(contestId)
                    .ifPresent(contest -> finalizeLocked(contest, event, actorId, awards, now));
        }
    }

    /**
     * The ordering rule, in one place: most votes first; on a tie the car that <em>reached</em>
     * the count first (older {@code last_vote_at}); then car id, so the order is total.
     */
    public static List<ContestEntryEntity> rank(List<ContestEntryEntity> entries) {
        List<ContestEntryEntity> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator
                .comparingInt(ContestEntryEntity::getVotesCount).reversed()
                .thenComparing(ContestEntryEntity::getLastVoteAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(e -> e.getId().getCarId()));
        return sorted;
    }

    private void award(ContestEntity contest, MapEventEntity event, List<ContestEntryEntity> podium, Instant now) {
        String label = event == null ? contest.getTitle() : contest.getTitle() + " · " + event.getTitle();
        ReputationSource source = new ReputationSource(ReputationSourceType.CONTEST, contest.getId(), label);

        for (ContestEntryEntity entry : podium) {
            UUID owner = entry.getOwnerId();
            String reason = switch (entry.getFinalRank()) {
                case 1 -> ReputationReasons.CONTEST_FIRST_PLACE;
                case 2 -> ReputationReasons.CONTEST_SECOND_PLACE;
                default -> ReputationReasons.CONTEST_THIRD_PLACE;
            };
            // One trigger per rank, because awardForTrigger unlocks every badge bound to the
            // trigger it is given: a shared "podium" trigger would hand the silver and the bronze
            // to all three finishers.
            BadgeTrigger trigger = switch (entry.getFinalRank()) {
                case 1 -> BadgeTrigger.CONTEST_FIRST_PLACE;
                case 2 -> BadgeTrigger.CONTEST_SECOND_PLACE;
                default -> BadgeTrigger.CONTEST_THIRD_PLACE;
            };
            try {
                reputationService.award(owner, reason, source);
            } catch (RuntimeException e) {
                // A retired reason must not stop the result from being published; log and go on.
                log.warn("Reputation award {} for contest {} failed: {}", reason, contest.getId(), e.getMessage());
            }
            badgeService.awardForTrigger(owner, trigger, now);
        }
    }

    private void announceFinished(ContestEntity contest, MapEventEntity event, List<ContestEntryEntity> podium) {
        Set<UUID> podiumOwners = podium.stream().map(ContestEntryEntity::getOwnerId).collect(Collectors.toSet());
        List<UUID> carIds = podium.stream().map(e -> e.getId().getCarId()).toList();
        Map<UUID, CarSummaryDto> cars = garageService.findCarsByIds(carIds).stream()
                .collect(Collectors.toMap(CarSummaryDto::id, Function.identity(), (a, b) -> a));

        List<ContestFinishedEvent.Placement> placements = podium.stream()
                .map(e -> {
                    CarSummaryDto car = cars.get(e.getId().getCarId());
                    String name = car == null ? "Your car" : car.brand() + " " + car.model();
                    return new ContestFinishedEvent.Placement(
                            e.getOwnerId(), e.getId().getCarId(), e.getFinalRank(), name);
                })
                .toList();

        events.publishEvent(new ContestFinishedEvent(
                contest.getEventId(),
                event == null ? "" : event.getTitle(),
                contest.getId(),
                contest.getTitle(),
                podium.get(0).getId().getCarId(),
                placements,
                audience(contest, podiumOwners)));
    }

    /**
     * Attending RSVPs, owners of accepted cars in the event, plus accepted entrants,
     * de-duplicated, minus {@code exclude}. Car owners are in for the same reason they may vote:
     * bringing a car to the event is attending it, with or without an RSVP row.
     */
    private List<UUID> audience(ContestEntity contest, Set<UUID> exclude) {
        Set<UUID> ids = new LinkedHashSet<>(attendeeRepository.findUserIdsByEventIdAndStatus(
                contest.getEventId(), MapEventAttendeeEntity.ATTENDING));
        for (MapEventParticipantEntity participant : participantRepository.findByIdEventIdAndStatus(
                contest.getEventId(), MapEventParticipantEntity.ACCEPTED)) {
            ids.add(participant.getOwnerId());
        }
        for (ContestEntryEntity entry : entryRepository.findByIdContestIdAndStatus(
                contest.getId(), ContestEntryEntity.ACCEPTED)) {
            ids.add(entry.getOwnerId());
        }
        ids.removeAll(new HashSet<>(exclude));
        return new ArrayList<>(ids);
    }

    static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
