package com.carsocialmedia.backend.presence.internal;

import com.carsocialmedia.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-SQL behaviors of {@code user_presence} against the Supabase schema in a PostGIS container:
 * the native FK-safe upsert (insert then on-conflict update, silent no-op for a user with no
 * profile row), the batch {@code touchAll}, and the bulk {@code findAllById} the service reads
 * offline last-seens through.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserPresenceRepositoryIT extends AbstractPostgresIT {

    private static final UUID U1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID U2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID NO_PROFILE = UUID.fromString("00000000-0000-0000-0000-0000000000f9");

    private static final Instant T1 = Instant.parse("2026-07-16T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-16T09:30:00Z");

    @Autowired
    private UserPresenceRepository repository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        createUser(U1);
        createUser(U2);
    }

    private void createUser(UUID id) {
        jdbc.update("insert into auth.users (id) values (?)", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, "u_" + id);
    }

    private Instant lastSeenOf(UUID id) {
        return jdbc.queryForObject(
                "select last_seen_at from public.user_presence where user_id = ?", Instant.class, id);
    }

    private long rowCount(UUID id) {
        return jdbc.queryForObject(
                "select count(*) from public.user_presence where user_id = ?", Long.class, id);
    }

    @Test
    void upsertInsertsANewWatermarkRow() {
        repository.upsertLastSeen(U1, T1);

        assertThat(lastSeenOf(U1)).isEqualTo(T1);
    }

    @Test
    void upsertOnConflictUpdatesTheExistingWatermarkInPlace() {
        repository.upsertLastSeen(U1, T1);
        repository.upsertLastSeen(U1, T2);

        assertThat(lastSeenOf(U1)).isEqualTo(T2);
        assertThat(rowCount(U1)).isEqualTo(1L);
    }

    @Test
    void upsertForAUserWithoutAProfileRowIsASilentNoOp() {
        repository.upsertLastSeen(NO_PROFILE, T1); // no FK violation, no poisoned transaction

        assertThat(rowCount(NO_PROFILE)).isZero();
    }

    @Test
    void touchAllUpdatesTheWatermarkForEveryGivenUser() {
        repository.upsertLastSeen(U1, T1);
        repository.upsertLastSeen(U2, T1);

        repository.touchAll(List.of(U1, U2), T2);

        assertThat(lastSeenOf(U1)).isEqualTo(T2);
        assertThat(lastSeenOf(U2)).isEqualTo(T2);
    }

    @Test
    void findAllByIdReadsBackPersistedWatermarks() {
        repository.upsertLastSeen(U1, T1);
        repository.upsertLastSeen(U2, T2);

        Map<UUID, Instant> byId = repository.findAllById(List.of(U1, U2)).stream()
                .collect(Collectors.toMap(UserPresenceEntity::getUserId, UserPresenceEntity::getLastSeenAt));

        assertThat(byId).containsEntry(U1, T1).containsEntry(U2, T2);
    }
}
