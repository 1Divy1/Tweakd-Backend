package com.tweakdapp.backend.mapevents.internal.realtime;

import com.tweakdapp.backend.mapevents.internal.entities.ContestEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryEntity;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestEntryRepository;
import com.tweakdapp.backend.mapevents.internal.repositories.ContestRepository;
import com.tweakdapp.backend.shared.realtime.SupabaseBroadcastClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes the live leaderboard to everyone watching an event, over Supabase Realtime Broadcast.
 *
 * <p>Topic: {@code event:<event_id>:contests} — one channel per open event page, whatever the
 * number of contests. Two message kinds:
 * <ul>
 *   <li>{@code board} — the tallies of one contest: {@code {contest_id, status, votes_count,
 *       sent_at, entries: [{car_id, votes_count}]}}. Car ids and counts only; never a voter.</li>
 *   <li>{@code status} — {@code {contest_id, status}} when a contest opens, finishes, or has its
 *       window changed; clients re-read that contest.</li>
 * </ul>
 *
 * <p><strong>Debounced.</strong> A vote does not publish; it marks its contest dirty
 * ({@link #markDirty}), and {@link #flush} — every 1.5 s — reads one board per dirty contest and
 * sends it. So the message rate is bounded by the number of contests being voted on, not by the
 * number of votes, whatever a busy meet does. {@link #markDirty} must be called <em>after
 * commit</em>: a flush that ran between the mark and the commit would read the old count and
 * nothing would re-mark it.
 *
 * <p>Status changes publish immediately ({@link #publishStatus}) — they are rare and the app
 * should react to them at once.
 */
@Component
public class ContestBoardPublisher {

    private static final Logger log = LoggerFactory.getLogger(ContestBoardPublisher.class);

    /** Dirty contests, keyed by contest id → event id (the topic needs the latter). */
    private final ConcurrentHashMap<UUID, UUID> dirty = new ConcurrentHashMap<>();

    private final ContestRepository contestRepository;
    private final ContestEntryRepository entryRepository;
    private final SupabaseBroadcastClient broadcastClient;

    public ContestBoardPublisher(ContestRepository contestRepository,
                                 ContestEntryRepository entryRepository,
                                 SupabaseBroadcastClient broadcastClient) {
        this.contestRepository = contestRepository;
        this.entryRepository = entryRepository;
        this.broadcastClient = broadcastClient;
    }

    /** Queues a board publish for the next flush. Call after the vote has committed. */
    public void markDirty(UUID eventId, UUID contestId) {
        dirty.put(contestId, eventId);
    }

    /** Publishes a status change now, and queues the (final) board behind it. */
    public void publishStatus(UUID eventId, UUID contestId, String status) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contest_id", contestId.toString());
        payload.put("status", status);
        broadcastClient.broadcast(topic(eventId), "status", payload);
        markDirty(eventId, contestId);
    }

    @Scheduled(fixedDelay = 1500)
    @Transactional(readOnly = true)
    public void flush() {
        if (dirty.isEmpty()) {
            return;
        }
        // Snapshot and clear first, so a vote landing mid-flush re-marks for the next round rather
        // than being lost.
        Map<UUID, UUID> batch = new LinkedHashMap<>();
        for (UUID contestId : new ArrayList<>(dirty.keySet())) {
            UUID eventId = dirty.remove(contestId);
            if (eventId != null) {
                batch.put(contestId, eventId);
            }
        }
        for (Map.Entry<UUID, UUID> entry : batch.entrySet()) {
            try {
                publishBoard(entry.getValue(), entry.getKey());
            } catch (Exception e) {
                log.warn("Could not publish board for contest {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private void publishBoard(UUID eventId, UUID contestId) {
        ContestEntity contest = contestRepository.findById(contestId).orElse(null);
        if (contest == null) {
            return;
        }
        List<ContestEntryEntity> entries =
                entryRepository.findByIdContestIdAndStatus(contestId, ContestEntryEntity.ACCEPTED);

        List<Map<String, Object>> rows = new ArrayList<>(entries.size());
        for (ContestEntryEntity e : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("car_id", e.getId().getCarId().toString());
            row.put("votes_count", e.getVotesCount());
            row.put("last_vote_at", e.getLastVoteAt() == null ? null : e.getLastVoteAt().toString());
            rows.add(row);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contest_id", contestId.toString());
        payload.put("status", contest.getStatus());
        payload.put("votes_count", contest.getVotesCount());
        payload.put("sent_at", Instant.now().toString());
        payload.put("entries", rows);
        broadcastClient.broadcast(topic(eventId), "board", payload);
    }

    static String topic(UUID eventId) {
        return "event:" + eventId + ":contests";
    }
}
