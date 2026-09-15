package com.tweakdapp.backend.posts.internal.repositories;

import com.tweakdapp.backend.shared.blocking.BlockDirectory;
import com.tweakdapp.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-SQL behaviors of the global feed's keyset query: posts rank by {@code ranking_score}, a post
 * someone the viewer follows reposted ranks as if published at that repost, anyone else's repost
 * only counts as engagement, and a page resumes exactly after its cursor. Scores come from the real
 * Supabase triggers loaded with the schema dump.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostRankedFeedRepositoryIT extends AbstractPostgresIT {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000cc001");
    private static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-0000000cc002");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000cc003");
    private static final UUID FRIEND_2 = UUID.fromString("00000000-0000-0000-0000-0000000cc004");
    /** An author a block separates from the viewer. */
    private static final UUID BLOCKED = UUID.fromString("00000000-0000-0000-0000-0000000cc005");

    // Two days apart: far more freshness than one repost's worth of engagement can make up.
    private static final Instant OLD = Instant.parse("2026-07-20T10:00:00Z");
    private static final Instant NEW = Instant.parse("2026-07-22T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-07-23T10:00:00Z");
    private static final Instant LATEST = Instant.parse("2026-07-23T12:00:00Z");

    private static final String NO_FOLLOWEES = "{}";

    @Autowired
    private PostRepository postRepository;
    @Autowired
    private PostShareRepository postShareRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        createUser(AUTHOR);
        createUser(FRIEND);
        createUser(STRANGER);
        createUser(FRIEND_2);
        createUser(BLOCKED);
    }

    @Test
    void ranksByRankingScoreWhenNoFolloweeReposted() {
        UUID older = insertPost(AUTHOR, OLD);
        UUID newer = insertPost(AUTHOR, NEW);

        assertThat(firstPage(NO_FOLLOWEES)).containsExactly(newer, older);
    }

    @Test
    void aFolloweesRepostRanksThePostAsFreshAsTheRepost() {
        UUID older = insertPost(AUTHOR, OLD);
        UUID newer = insertPost(AUTHOR, NEW);
        repost(older, FRIEND, LATER);

        assertThat(firstPage(array(FRIEND))).containsExactly(older, newer);
    }

    @Test
    void aStrangersRepostIsOnlyEngagement() {
        UUID older = insertPost(AUTHOR, OLD);
        UUID newer = insertPost(AUTHOR, NEW);
        repost(older, STRANGER, LATER);

        assertThat(firstPage(array(FRIEND))).containsExactly(newer, older);
    }

    @Test
    void theBoostKeepsTheEngagementThePostEarned() {
        UUID older = insertPost(AUTHOR, OLD);
        repost(older, FRIEND, LATER);

        List<RankedPostRow> rows = postRepository.findRankedPostPage(
                array(FRIEND), "{}", Double.POSITIVE_INFINITY, new UUID(0, 0), 10);
        double rankingScore = jdbc.queryForObject(
                "select ranking_score from public.posts where id = ?", Double.class, older);

        double boost = (LATER.getEpochSecond() - OLD.getEpochSecond()) / 45000.0;
        assertThat(rows.getFirst().getScore()).isCloseTo(rankingScore + boost, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    void aPageResumesStrictlyAfterItsCursorAcrossBothPopulations() {
        UUID a = insertPost(AUTHOR, OLD);
        UUID b = insertPost(AUTHOR, NEW);
        UUID c = insertPost(AUTHOR, Instant.parse("2026-07-21T10:00:00Z"));
        repost(a, FRIEND, LATER);

        List<RankedPostRow> page1 = postRepository.findRankedPostPage(
                array(FRIEND), "{}", Double.POSITIVE_INFINITY, new UUID(0, 0), 2);
        RankedPostRow last = page1.getLast();
        List<RankedPostRow> page2 = postRepository.findRankedPostPage(
                array(FRIEND), "{}", last.getScore(), last.getId(), 2);

        assertThat(page1).extracting(RankedPostRow::getId).containsExactly(a, b);
        assertThat(page2).extracting(RankedPostRow::getId).containsExactly(c);
    }

    @Test
    void followedRepostersComeBackMostRecentFirst() {
        UUID post = insertPost(AUTHOR, OLD);
        repost(post, FRIEND, LATER);
        repost(post, FRIEND_2, LATEST);
        repost(post, STRANGER, LATEST);

        List<RepostRow> rows = postShareRepository.findFollowedReposts(List.of(post), array(FRIEND, FRIEND_2));

        assertThat(rows).extracting(RepostRow::getUserId).containsExactly(FRIEND_2, FRIEND);
        assertThat(rows).extracting(RepostRow::getPostId).containsOnly(post);
    }

    // ---- blocking: hidden authors ---------------------------------------------

    @Test
    void aHiddenAuthorsPostIsLeftOutOfThePlainBranchWithoutShorteningThePage() {
        UUID a = insertPost(AUTHOR, OLD);
        UUID c = insertPost(AUTHOR, Instant.parse("2026-07-21T10:00:00Z"));
        insertPost(BLOCKED, NEW); // would rank first

        List<RankedPostRow> page = postRepository.findRankedPostPage(
                NO_FOLLOWEES, array(BLOCKED), Double.POSITIVE_INFINITY, new UUID(0, 0), 2);

        assertThat(page).extracting(RankedPostRow::getId).containsExactly(c, a);
    }

    @Test
    void aHiddenAuthorsPostRepostedByAFolloweeIsLeftOutOfTheBoostedBranch() {
        UUID hidden = insertPost(BLOCKED, OLD);
        UUID newer = insertPost(AUTHOR, NEW);
        repost(hidden, FRIEND, LATER); // would boost it to the top

        List<UUID> ids = postRepository.findRankedPostPage(
                        array(FRIEND), array(BLOCKED), Double.POSITIVE_INFINITY, new UUID(0, 0), 10)
                .stream().map(RankedPostRow::getId).toList();

        assertThat(ids).containsExactly(newer);
    }

    @Test
    void theRepostsTabSkipsPostsByAHiddenAuthor() {
        UUID visible = insertPost(AUTHOR, OLD);
        UUID hidden = insertPost(BLOCKED, OLD);
        repost(visible, STRANGER, LATER);
        repost(hidden, STRANGER, LATEST);

        List<UUID> filtered = postShareRepository.findSharedPage(
                        STRANGER, true, null, null, List.of(BLOCKED), PageRequest.of(0, 10))
                .stream().map(ps -> ps.getId().getPostId()).toList();
        List<UUID> unfiltered = postShareRepository.findSharedPage(
                        STRANGER, true, null, null, List.of(BlockDirectory.NOBODY), PageRequest.of(0, 10))
                .stream().map(ps -> ps.getId().getPostId()).toList();

        assertThat(filtered).containsExactly(visible);
        assertThat(unfiltered).containsExactly(hidden, visible);
    }

    // ---- helpers ------------------------------------------------------------

    private List<UUID> firstPage(String followees) {
        return postRepository.findRankedPostPage(followees, "{}", Double.POSITIVE_INFINITY, new UUID(0, 0), 10)
                .stream().map(RankedPostRow::getId).toList();
    }

    private static String array(UUID... ids) {
        return "{" + String.join(",", java.util.Arrays.stream(ids).map(UUID::toString).toList()) + "}";
    }

    private void createUser(UUID id) {
        jdbc.update("insert into auth.users (id) values (?)", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, "u_" + id);
    }

    private UUID insertPost(UUID author, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.posts (id, user_id, description, created_at) values (?, ?, ?, ?)",
                id, author, "post", at(createdAt));
        return id;
    }

    private void repost(UUID postId, UUID userId, Instant at) {
        jdbc.update("insert into public.post_shares (post_id, user_id, created_at) values (?, ?, ?)",
                postId, userId, at(at));
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
