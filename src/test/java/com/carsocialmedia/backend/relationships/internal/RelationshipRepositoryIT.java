package com.carsocialmedia.backend.relationships.internal;

import com.carsocialmedia.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Real-SQL behaviors of {@link RelationshipRepository} and the {@code follows} table against the
 * Supabase schema in a PostGIS container: the accepted-only follower/following queries and their
 * newest-first ordering, the "which of these do I follow" subset query, the composite-PK
 * uniqueness of a follow pair, the {@code no_self_follow} check constraint, and the
 * {@code handle_follow_change} trigger that maintains the profile follower/following counters.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RelationshipRepositoryIT extends AbstractPostgresIT {

    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID VIEWER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID F1 = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID F2 = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final UUID F3 = UUID.fromString("00000000-0000-0000-0000-0000000000f3");

    @Autowired
    private RelationshipRepository relationshipRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    // ---- fixture helpers ----------------------------------------------------

    private void createProfile(UUID id) {
        jdbc.update("insert into auth.users (id) values (?)", id);
        // Username is UNIQUE — derive from the full UUID to avoid collisions.
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, "u_" + id);
    }

    /** Inserts an (accepted) follow row with an explicit created_at to control query ordering. */
    private void follow(UUID follower, UUID following, String createdAt) {
        jdbc.update("insert into public.follows (follower_id, following_id, created_at) values (?, ?, ?)",
                follower, following, OffsetDateTime.parse(createdAt).withOffsetSameInstant(ZoneOffset.UTC));
    }

    private long followersCount(UUID id) {
        return jdbc.queryForObject("select followers_count from public.profiles where id = ?", Long.class, id);
    }

    private long followingCount(UUID id) {
        return jdbc.queryForObject("select following_count from public.profiles where id = ?", Long.class, id);
    }

    // ---- follower / following list queries ----------------------------------

    @Test
    void findAcceptedFollowerIdsReturnsFollowersNewestFirst() {
        createProfile(TARGET);
        createProfile(F1);
        createProfile(F2);
        follow(F1, TARGET, "2026-07-16T10:00:00Z"); // older
        follow(F2, TARGET, "2026-07-16T11:00:00Z"); // newer

        assertThat(relationshipRepository.findAcceptedFollowerIds(TARGET))
                .containsExactly(F2, F1);
    }

    @Test
    void findAcceptedFollowingIdsReturnsWhoTheUserFollowsNewestFirst() {
        createProfile(TARGET);
        createProfile(F1);
        createProfile(F2);
        follow(TARGET, F1, "2026-07-16T10:00:00Z"); // older
        follow(TARGET, F2, "2026-07-16T11:00:00Z"); // newer

        assertThat(relationshipRepository.findAcceptedFollowingIds(TARGET))
                .containsExactly(F2, F1);
    }

    @Test
    void findAcceptedFollowingIdsInReturnsOnlyTheFollowedSubset() {
        createProfile(VIEWER);
        createProfile(F1);
        createProfile(F2);
        createProfile(F3);
        follow(VIEWER, F1, "2026-07-16T10:00:00Z");
        follow(VIEWER, F3, "2026-07-16T10:05:00Z");
        // VIEWER does NOT follow F2.

        assertThat(relationshipRepository.findAcceptedFollowingIdsIn(VIEWER, List.of(F1, F2, F3)))
                .containsExactlyInAnyOrder(F1, F3);
    }

    @Test
    void acceptedOnlyQueriesExcludeNonAcceptedRows() {
        createProfile(TARGET);
        createProfile(F1);
        follow(F1, TARGET, "2026-07-16T10:00:00Z");
        // The BEFORE INSERT trigger forces 'accepted'; flip it to a non-accepted state afterward
        // to prove the query's status filter excludes it.
        jdbc.update("update public.follows set status = 'pending' where follower_id = ? and following_id = ?",
                F1, TARGET);

        assertThat(relationshipRepository.findAcceptedFollowerIds(TARGET)).isEmpty();
    }

    // ---- constraints --------------------------------------------------------

    @Test
    void aDuplicateFollowPairViolatesThePrimaryKey() {
        createProfile(VIEWER);
        createProfile(TARGET);
        follow(VIEWER, TARGET, "2026-07-16T10:00:00Z");

        assertThatExceptionOfType(DuplicateKeyException.class).isThrownBy(() ->
                follow(VIEWER, TARGET, "2026-07-16T11:00:00Z"));
    }

    @Test
    void aSelfFollowIsRejectedByTheCheckConstraint() {
        createProfile(VIEWER);

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                follow(VIEWER, VIEWER, "2026-07-16T10:00:00Z"));
    }

    // ---- trigger-maintained counters ----------------------------------------

    @Test
    void insertingAndDeletingAFollowMovesTheTriggerMaintainedCounters() {
        createProfile(VIEWER);
        createProfile(TARGET);
        assertThat(followingCount(VIEWER)).isZero();
        assertThat(followersCount(TARGET)).isZero();

        follow(VIEWER, TARGET, "2026-07-16T10:00:00Z");

        assertThat(followingCount(VIEWER)).isEqualTo(1L);
        assertThat(followersCount(TARGET)).isEqualTo(1L);

        jdbc.update("delete from public.follows where follower_id = ? and following_id = ?", VIEWER, TARGET);

        assertThat(followingCount(VIEWER)).isZero();
        assertThat(followersCount(TARGET)).isZero();
    }
}
