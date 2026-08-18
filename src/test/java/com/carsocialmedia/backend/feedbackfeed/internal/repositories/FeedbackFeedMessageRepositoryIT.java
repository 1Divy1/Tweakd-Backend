package com.carsocialmedia.backend.feedbackfeed.internal.repositories;

import com.carsocialmedia.backend.feedbackfeed.internal.entities.FeedbackFeedMessageEntity;
import com.carsocialmedia.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-SQL behaviour of the feedback feed.
 *
 * <p>Two things here can only be proven by running them against Postgres. The paged reads are
 * native queries built on row-value comparisons with {@code cast(...)}ed cursor parameters — a
 * missing cast compiles fine and then fails at runtime on the first page, when the cursor is null.
 * And the vote counters, the completed-vote block and the {@code completed_at} stamp are all
 * trigger behaviour the application deliberately does not implement.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FeedbackFeedMessageRepositoryIT extends AbstractPostgresIT {

    private static final int PAGE = 10;

    @Autowired
    private FeedbackFeedMessageRepository messageRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private UUID author;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from public.feedback_feed_votes");
        jdbc.update("delete from public.feedback_feed_messages");
        author = profile("feedbacker_" + UUID.randomUUID().toString().substring(0, 8));
    }

    // ---- the paged reads ----------------------------------------------------

    @Test
    void firstPageOfEverySortRunsWithNullCursorParameters() {
        message("oldest", "bug", "sent", 5, hoursAgo(3));
        message("middle", "feature_request", "under_development", 20, hoursAgo(2));
        message("newest", "feature_improvement", "sent", 1, hoursAgo(1));

        assertThat(texts(messageRepository.findNewest(true, null, null, PAGE)))
                .containsExactly("newest", "middle", "oldest");
        assertThat(texts(messageRepository.findOldest(true, null, null, PAGE)))
                .containsExactly("oldest", "middle", "newest");
        assertThat(texts(messageRepository.findMostVoted(true, null, null, PAGE)))
                .containsExactly("middle", "oldest", "newest");
        assertThat(messageRepository.findCompleted(true, null, null, PAGE)).isEmpty();
        assertThat(texts(messageRepository.findForAdmin(null, null, false, true, null, null, PAGE)))
                .containsExactly("newest", "middle", "oldest");
    }

    @Test
    void keysetPagingWalksEverySortWithoutSkippingOrRepeating() {
        for (int i = 0; i < 5; i++) {
            message("m" + i, "bug", "sent", i, hoursAgo(5 - i));
        }

        assertThat(walkByTime(false)).containsExactly("m4", "m3", "m2", "m1", "m0");
        assertThat(walkByTime(true)).containsExactly("m0", "m1", "m2", "m3", "m4");
        assertThat(walkByScore()).containsExactly("m4", "m3", "m2", "m1", "m0");
    }

    @Test
    void tiesOnTheSortKeyAreBrokenByIdSoNoRowIsLostAcrossPages() {
        // Same net_votes on every row: without the id tiebreaker the keyset would stall or repeat.
        for (int i = 0; i < 5; i++) {
            message("tied" + i, "bug", "sent", 7, hoursAgo(1));
        }
        assertThat(walkByScore()).hasSize(5).doesNotHaveDuplicates();
    }

    @Test
    void completedMessagesLeaveTheFeedAndOrderByShipDateNotSubmissionDate() {
        // Submitted first, shipped last — the two orderings disagree on purpose.
        UUID oldSubmission = message("shipped last", "bug", "sent", 0, hoursAgo(10));
        UUID newSubmission = message("shipped first", "bug", "sent", 0, hoursAgo(1));
        message("still open", "bug", "sent", 0, hoursAgo(5));

        // Explicit ship dates: the stamping trigger uses now(), which is the *transaction* clock, so
        // two completions inside one test transaction would land on the identical timestamp.
        completeAt(newSubmission, hoursAgo(4));
        completeAt(oldSubmission, hoursAgo(2));

        assertThat(texts(messageRepository.findNewest(true, null, null, PAGE)))
                .containsExactly("still open");
        assertThat(texts(messageRepository.findCompleted(true, null, null, PAGE)))
                .containsExactly("shipped last", "shipped first");
    }

    @Test
    void staffRemovedMessagesDisappearFromEveryUserFacingReadButRemainForTheDashboard() {
        UUID spam = message("spam", "bug", "sent", 99, hoursAgo(1));
        message("genuine", "bug", "sent", 1, hoursAgo(2));
        jdbc.update("update public.feedback_feed_messages set is_deleted = true where id = ?", spam);

        assertThat(texts(messageRepository.findNewest(true, null, null, PAGE))).containsExactly("genuine");
        assertThat(texts(messageRepository.findMostVoted(true, null, null, PAGE))).containsExactly("genuine");
        assertThat(messageRepository.findByIdAndDeletedFalse(spam)).isEmpty();

        assertThat(texts(messageRepository.findForAdmin(null, null, false, true, null, null, PAGE)))
                .containsExactly("genuine");
        assertThat(texts(messageRepository.findForAdmin(null, null, true, true, null, null, PAGE)))
                .containsExactlyInAnyOrder("spam", "genuine");
    }

    @Test
    void adminFiltersAreOptionalAndCombine() {
        message("a bug", "bug", "sent", 0, hoursAgo(3));
        message("a request", "feature_request", "sent", 0, hoursAgo(2));
        UUID inProgress = message("an improvement", "feature_improvement", "sent", 0, hoursAgo(1));
        jdbc.update("update public.feedback_feed_messages set status = 'under_development' where id = ?",
                inProgress);

        assertThat(texts(messageRepository.findForAdmin("bug", null, false, true, null, null, PAGE)))
                .containsExactly("a bug");
        assertThat(texts(messageRepository.findForAdmin(null, "under_development", false, true, null, null, PAGE)))
                .containsExactly("an improvement");
        assertThat(texts(messageRepository.findForAdmin("bug", "under_development", false, true, null, null, PAGE)))
                .isEmpty();
    }

    // ---- the triggers the application relies on ------------------------------

    @Test
    void voteTriggerMaintainsTheCountersThroughInsertSwitchAndDelete() {
        UUID message = message("counted", "bug", "sent", 0, hoursAgo(1));
        UUID up = profile("up_" + UUID.randomUUID().toString().substring(0, 8));
        UUID down = profile("down_" + UUID.randomUUID().toString().substring(0, 8));

        vote(up, message, 1);
        vote(down, message, -1);
        assertCounts(message, 1, 1, 0);

        // Switching direction has to move both counters, not just one.
        jdbc.update("update public.feedback_feed_votes set vote_type = 1 where user_id = ? and message_id = ?",
                down, message);
        assertCounts(message, 2, 0, 2);

        jdbc.update("delete from public.feedback_feed_votes where user_id = ? and message_id = ?", up, message);
        assertCounts(message, 1, 0, 1);
    }

    @Test
    void completingAMessageStampsCompletedAtAndMovingItBackClearsIt() {
        UUID message = message("shipping", "bug", "sent", 0, hoursAgo(1));
        assertThat(completedAt(message)).isNull();

        complete(message);
        assertThat(completedAt(message)).isNotNull();

        jdbc.update("update public.feedback_feed_messages set status = 'under_development' where id = ?", message);
        assertThat(completedAt(message)).isNull();
    }

    // The two blocked-vote cases are separate tests on purpose: the trigger raises, which aborts the
    // surrounding transaction, so a second failing statement in the same test would only ever report
    // "current transaction is aborted".

    @Test
    void databaseRefusesNewVotesOnCompletedMessages() {
        UUID message = message("shipped", "bug", "sent", 0, hoursAgo(1));
        complete(message);
        UUID voter = profile("late_" + UUID.randomUUID().toString().substring(0, 8));

        assertThatThrownBy(() -> vote(voter, message, 1))
                .hasMessageContaining("Cannot vote on a completed feedback message");
    }

    @Test
    void databaseRefusesWithdrawingAVoteFromACompletedMessage() {
        // The tally is frozen once shipped, not merely closed to newcomers.
        UUID message = message("shipped", "bug", "sent", 0, hoursAgo(1));
        UUID voter = profile("early_" + UUID.randomUUID().toString().substring(0, 8));
        vote(voter, message, 1);
        complete(message);

        assertThatThrownBy(() -> jdbc.update(
                "delete from public.feedback_feed_votes where user_id = ? and message_id = ?", voter, message))
                .hasMessageContaining("Cannot remove a vote from a completed feedback message");
    }

    @Test
    void deletingAMessageCascadesToItsVotes() {
        UUID message = message("doomed", "bug", "sent", 0, hoursAgo(1));
        UUID voter = profile("voter_" + UUID.randomUUID().toString().substring(0, 8));
        vote(voter, message, 1);

        messageRepository.deleteById(message);
        messageRepository.flush();

        assertThat(jdbc.queryForObject(
                "select count(*) from public.feedback_feed_votes where message_id = ?", Integer.class, message))
                .isZero();
    }

    // ---- fixture helpers ----------------------------------------------------

    private UUID profile(String username) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into auth.users (id, email) values (?, ?)", id, username + "@test.local");
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, username);
        return id;
    }

    /**
     * Inserts a message with the counters written directly. The vote trigger owns those columns in
     * production, but seeding them here keeps the sort tests from needing dozens of voter profiles.
     */
    private UUID message(String text, String type, String status, int netVotes, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into public.feedback_feed_messages
                    (id, author_id, message, type, status, net_votes, up_votes, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, author, text, type, status, netVotes, Math.max(netVotes, 0), Timestamp.from(createdAt));
        return id;
    }

    private void complete(UUID messageId) {
        jdbc.update("update public.feedback_feed_messages set status = 'completed' where id = ?", messageId);
    }

    /** Completes a message, then overrides the trigger's stamp so ship dates can be ordered. */
    private void completeAt(UUID messageId, Instant shippedAt) {
        complete(messageId);
        jdbc.update("update public.feedback_feed_messages set completed_at = ? where id = ?",
                Timestamp.from(shippedAt), messageId);
    }

    private void vote(UUID userId, UUID messageId, int voteType) {
        jdbc.update("insert into public.feedback_feed_votes (user_id, message_id, vote_type) values (?, ?, ?)",
                userId, messageId, voteType);
    }

    private Instant completedAt(UUID messageId) {
        Timestamp stamp = jdbc.queryForObject(
                "select completed_at from public.feedback_feed_messages where id = ?", Timestamp.class, messageId);
        return stamp == null ? null : stamp.toInstant();
    }

    private void assertCounts(UUID messageId, int up, int down, int net) {
        assertThat(jdbc.queryForMap(
                "select up_votes, down_votes, net_votes from public.feedback_feed_messages where id = ?", messageId))
                .containsEntry("up_votes", up)
                .containsEntry("down_votes", down)
                .containsEntry("net_votes", net);
    }

    /** Pages one row at a time through a time-ordered sort, following the cursor to exhaustion. */
    private List<String> walkByTime(boolean ascending) {
        List<String> seen = new java.util.ArrayList<>();
        Instant cursorAt = null;
        UUID cursorId = null;
        while (true) {
            List<FeedbackFeedMessageEntity> rows = ascending
                    ? messageRepository.findOldest(cursorId == null, cursorAt, cursorId, 1)
                    : messageRepository.findNewest(cursorId == null, cursorAt, cursorId, 1);
            if (rows.isEmpty()) {
                return seen;
            }
            FeedbackFeedMessageEntity last = rows.getLast();
            seen.add(last.getMessage());
            cursorAt = last.getCreatedAt();
            cursorId = last.getId();
        }
    }

    private List<String> walkByScore() {
        List<String> seen = new java.util.ArrayList<>();
        Integer cursorScore = null;
        UUID cursorId = null;
        while (true) {
            List<FeedbackFeedMessageEntity> rows =
                    messageRepository.findMostVoted(cursorId == null, cursorScore, cursorId, 1);
            if (rows.isEmpty()) {
                return seen;
            }
            FeedbackFeedMessageEntity last = rows.getLast();
            seen.add(last.getMessage());
            cursorScore = last.getNetVotes();
            cursorId = last.getId();
        }
    }

    private static List<String> texts(List<FeedbackFeedMessageEntity> rows) {
        return rows.stream().map(FeedbackFeedMessageEntity::getMessage).toList();
    }

    private static Instant hoursAgo(int hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }
}
