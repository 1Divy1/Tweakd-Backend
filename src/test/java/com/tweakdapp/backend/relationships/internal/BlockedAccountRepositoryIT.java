package com.tweakdapp.backend.relationships.internal;

import com.tweakdapp.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-SQL behaviors of {@link BlockedAccountRepository} against the Supabase schema: the two-way
 * counterpart lookup behind every block filter, the symmetric existence check, the newest-first
 * settings list, and {@link RelationshipRepository#deleteFollowsBetween} removing follows in both
 * directions (with the follower counters kept right by the trigger).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BlockedAccountRepositoryIT extends AbstractPostgresIT {

    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID BLOCKED_BY_ME = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID BLOCKS_ME = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000b4");

    @Autowired
    private BlockedAccountRepository blockedAccountRepository;
    @Autowired
    private RelationshipRepository relationshipRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        for (UUID id : new UUID[]{ME, BLOCKED_BY_ME, BLOCKS_ME, STRANGER}) {
            jdbc.update("insert into auth.users (id) values (?)", id);
            jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, "u_" + id);
        }
    }

    private void block(UUID blocker, UUID blocked, String createdAt) {
        jdbc.update("insert into public.blocked_accounts (blocker_id, blocked_id, created_at) values (?, ?, ?)",
                blocker, blocked, OffsetDateTime.parse(createdAt).withOffsetSameInstant(ZoneOffset.UTC));
    }

    @Test
    void counterpartIdsCoverBothDirections() {
        block(ME, BLOCKED_BY_ME, "2026-09-14T10:00:00Z");
        block(BLOCKS_ME, ME, "2026-09-14T11:00:00Z");

        assertThat(blockedAccountRepository.findCounterpartIds(ME))
                .containsExactlyInAnyOrder(BLOCKED_BY_ME, BLOCKS_ME);
        assertThat(blockedAccountRepository.findCounterpartIds(BLOCKS_ME)).containsExactly(ME);
        assertThat(blockedAccountRepository.findCounterpartIds(STRANGER)).isEmpty();
    }

    @Test
    void existsBetweenIsSymmetric() {
        block(ME, BLOCKED_BY_ME, "2026-09-14T10:00:00Z");

        assertThat(blockedAccountRepository.existsBetween(ME, BLOCKED_BY_ME)).isTrue();
        assertThat(blockedAccountRepository.existsBetween(BLOCKED_BY_ME, ME)).isTrue();
        assertThat(blockedAccountRepository.existsBetween(ME, STRANGER)).isFalse();
    }

    @Test
    void settingsListIsTheBlockersOwnBlocksNewestFirst() {
        block(ME, STRANGER, "2026-09-01T10:00:00Z");
        block(ME, BLOCKED_BY_ME, "2026-09-14T10:00:00Z");
        block(BLOCKS_ME, ME, "2026-09-10T10:00:00Z"); // someone else's block: not listed

        assertThat(blockedAccountRepository.findByBlockerNewestFirst(ME))
                .extracting(row -> row.getId().getBlockedId())
                .containsExactly(BLOCKED_BY_ME, STRANGER);
    }

    @Test
    void deleteFollowsBetweenRemovesBothDirectionsAndKeepsCountersRight() {
        jdbc.update("insert into public.follows (follower_id, following_id) values (?, ?)", ME, BLOCKED_BY_ME);
        jdbc.update("insert into public.follows (follower_id, following_id) values (?, ?)", BLOCKED_BY_ME, ME);
        jdbc.update("insert into public.follows (follower_id, following_id) values (?, ?)", STRANGER, ME);

        int deleted = relationshipRepository.deleteFollowsBetween(ME, BLOCKED_BY_ME);

        assertThat(deleted).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from public.follows", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select followers_count from public.profiles where id = ?", Long.class, ME))
                .isEqualTo(1);
    }
}
