package com.tweakdapp.backend.garage.internal.repositories;

import com.tweakdapp.backend.garage.internal.entities.CarShareLinkEntity;
import com.tweakdapp.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The guarantees the {@code car_share_links} schema makes, against the real Supabase shape in a
 * container. The service leans on every one of these rather than re-implementing it:
 *
 * <ul>
 *   <li>a code is never reused — the unique index spans revoked rows, so a retired code can never
 *       come back pointing at a different car than the sticker it is printed on;</li>
 *   <li>a car has at most one live link — the partial unique index, which is what makes minting
 *       safe under two simultaneous taps of "Share" rather than only under a well-timed
 *       pre-check;</li>
 *   <li>the code format check, which is the other half of the normalise-on-lookup contract;</li>
 *   <li>deleting a car takes its links with it, so a deleted car's code 404s instead of dangling;</li>
 *   <li>{@code recordView} increments in the database, which is why concurrent scans do not lose
 *       counts.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DirtiesContext
class CarShareLinkRepositoryIT extends AbstractPostgresIT {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @Autowired
    private CarShareLinkRepository repository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private UUID carId;
    private UUID otherCarId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from public.car_share_links");
        jdbc.update("delete from public.cars");
        jdbc.update("delete from public.garages");

        createProfile(OWNER, "racer_s1");
        createProfile(OTHER_OWNER, "racer_s2");
        carId = createCar(OWNER);
        otherCarId = createCar(OTHER_OWNER);
    }

    // ---- the code is never reused ------------------------------------------

    @Test
    void twoLinksCannotShareACode() {
        insertLink(carId, OWNER, "7KQ3M9XA2F", true, null);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertLink(otherCarId, OTHER_OWNER, "7KQ3M9XA2F", true, null));
    }

    /**
     * The one that protects a printed sticker: even after a code is retired, it stays taken. If
     * uniqueness were partial like the live-link index, a later car could be issued the same code
     * and an old sticker would start pointing at a stranger's build.
     */
    @Test
    void aRevokedCodeStaysTaken() {
        insertLink(carId, OWNER, "7KQ3M9XA2F", true, Instant.now());

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertLink(otherCarId, OTHER_OWNER, "7KQ3M9XA2F", true, null));
    }

    // ---- one live link per car ---------------------------------------------

    /** What makes {@code ensureShareLink} idempotent under concurrency, not just under a pre-check. */
    @Test
    void aCarCannotHaveTwoLiveLinks() {
        insertLink(carId, OWNER, "AAAAAAAAAA", true, null);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertLink(carId, OWNER, "BBBBBBBBBB", true, null));
    }

    /** After a transfer retires the old row, the new owner's first share must succeed. */
    @Test
    void aCarCanBeSharedAgainOnceTheOldLinkIsRevoked() {
        insertLink(carId, OWNER, "AAAAAAAAAA", true, Instant.now());

        insertLink(carId, OTHER_OWNER, "BBBBBBBBBB", true, null);

        assertThat(repository.findLiveByCarId(carId))
                .map(CarShareLinkEntity::getCode)
                .contains("BBBBBBBBBB");
    }

    /** A paused link is still live — that is the whole difference between pausing and revoking. */
    @Test
    void pausingDoesNotFreeTheCarForANewLink() {
        insertLink(carId, OWNER, "AAAAAAAAAA", false, null);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertLink(carId, OWNER, "BBBBBBBBBB", true, null));
    }

    // ---- the code format ----------------------------------------------------

    /**
     * The database half of the normalise-on-lookup contract. If a lowercase or an O-bearing code
     * could be stored, a typed-in code folded to canonical form would stop matching it.
     *
     * <p>One case per invocation, not one test with four assertions: the first constraint violation
     * aborts the Postgres transaction, and every statement after it fails for that reason instead of
     * its own.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "7kq3m9xa2f",   // lowercase
            "7KQ3M9XAOF",   // O, which normalize folds to 0
            "7KQ3M9XAIF",   // I, which normalize folds to 1
            "7KQ3M9XALF",   // L, likewise
            "7KQ3M9XAUF",   // U, excluded from the alphabet entirely
            "7KQ3M9XA",     // too short
            "7KQ3M9XA2FF",  // too long
            "7KQ3-M9XA2",   // the grouping the app displays is not what is stored
    })
    void onlyCanonicalCodesCanBeStored(String code) {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertLink(carId, OWNER, code, true, null));
    }

    // ---- reads --------------------------------------------------------------

    /** Revoked rows must still resolve, or a retired code would 404 where it should answer 410. */
    @Test
    void findByCodeReturnsRevokedRowsButFindLiveDoesNot() {
        insertLink(carId, OWNER, "AAAAAAAAAA", true, Instant.now());

        assertThat(repository.findByCode("AAAAAAAAAA")).isPresent();
        assertThat(repository.findLiveByCarId(carId)).isEmpty();
    }

    @Test
    void ownerLookupSkipsRevokedLinks() {
        insertLink(carId, OWNER, "AAAAAAAAAA", true, Instant.now());
        UUID second = createCar(OWNER);
        insertLink(second, OWNER, "BBBBBBBBBB", true, null);

        assertThat(repository.findByOwnerIdAndRevokedAtIsNull(OWNER))
                .extracting(CarShareLinkEntity::getCode)
                .containsExactly("BBBBBBBBBB");
    }

    // ---- counting -----------------------------------------------------------

    /**
     * The increment happens in the database, which is why two simultaneous scans of the same
     * sticker both land instead of one overwriting the other's read-modify-write.
     */
    @Test
    void recordViewIncrementsInTheDatabase() {
        UUID linkId = insertLink(carId, OWNER, "AAAAAAAAAA", true, null);
        Instant now = Instant.parse("2026-09-04T18:30:00Z");

        assertThat(repository.recordView(linkId, 1, 0, now)).isEqualTo(1);
        assertThat(repository.recordView(linkId, 0, 1, now)).isEqualTo(1);
        assertThat(repository.recordView(linkId, 0, 1, now)).isEqualTo(1);

        assertThat(jdbc.queryForObject(
                "select view_count from public.car_share_links where id = ?", Long.class, linkId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select qr_scan_count from public.car_share_links where id = ?", Long.class, linkId))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "select last_viewed_at from public.car_share_links where id = ?", Instant.class, linkId))
                .isEqualTo(now);
    }

    @Test
    void recordViewOnAMissingLinkChangesNothing() {
        assertThat(repository.recordView(UUID.randomUUID(), 1, 0, Instant.now())).isZero();
    }

    // ---- cascades -----------------------------------------------------------

    /** A deleted car's code must 404, not dangle: the link goes with the car. */
    @Test
    void deletingTheCarRemovesItsLinks() {
        insertLink(carId, OWNER, "AAAAAAAAAA", true, null);

        jdbc.update("delete from public.cars where id = ?", carId);

        assertThat(repository.findByCode("AAAAAAAAAA")).isEmpty();
    }

    @Test
    void deletingTheOwnerRemovesTheirLinks() {
        insertLink(carId, OWNER, "AAAAAAAAAA", true, null);

        jdbc.update("delete from public.profiles where id = ?", OWNER);

        assertThat(repository.findByCode("AAAAAAAAAA")).isEmpty();
    }

    // ---- fixtures -----------------------------------------------------------

    private void createProfile(UUID id, String username) {
        jdbc.update("insert into auth.users (id) values (?) on conflict do nothing", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?) on conflict do nothing",
                id, username);
    }

    private UUID createCar(UUID ownerId) {
        UUID garageId = jdbc.query(
                "select id from public.garages where owner_id = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                ownerId);
        if (garageId == null) {
            garageId = UUID.randomUUID();
            jdbc.update("insert into public.garages (id, owner_id) values (?, ?)", garageId, ownerId);
        }

        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into public.cars (id, garage_id, brand_id, model_id, drivetrain_id, year,
                                         horsepower, torque, weight, engine_displacement, color_id,
                                         mileage_unit_id, status_id, fuel_id)
                values (?, ?, '00000000-0000-0000-0000-0000000b0001', '00000000-0000-0000-0000-0000000d0001',
                        'test_dt', 2022, 635, 750, 1900, 4.4, 'test_color', 'test_km', 'test_status', 'test_fuel')
                """, id, garageId);
        return id;
    }

    private UUID insertLink(UUID car, UUID owner, String code, boolean enabled, Instant revokedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into public.car_share_links (id, car_id, owner_id, code, is_enabled, revoked_at)
                values (?, ?, ?, ?, ?, ?)
                """, id, car, owner, code, enabled, revokedAt == null ? null : java.sql.Timestamp.from(revokedAt));
        return id;
    }
}
