package com.carsocialmedia.backend.mapevents.internal.repositories;

import com.carsocialmedia.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Real-SQL behaviour of the map-event tables in a PostGIS container: the {@code ST_DWithin} radius
 * search with its visibility, time and category filters, and the schema constraints the
 * {@code car_events_schema_hardening} migration added.
 *
 * <p>The radius query is native PostGIS with an interface projection, so it is only ever verified
 * by running it — a mistyped alias or a swapped lat/lng would compile perfectly well.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MapEventRepositoryIT extends AbstractPostgresIT {

    /** Cluj-Napoca city centre — the search origin for every case below. */
    private static final double CENTRE_LAT = 46.7712;
    private static final double CENTRE_LNG = 23.6236;

    private static final double RADIUS_25_KM = 25_000;

    @Autowired
    private MapEventRepository eventRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private UUID creator;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from public.car_event_organizers");
        jdbc.update("delete from public.event_car_meet");
        jdbc.update("delete from public.car_events");
        creator = profile("organizer_" + UUID.randomUUID().toString().substring(0, 8));
    }

    // ---- fixture helpers ----------------------------------------------------

    private UUID profile(String username) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into auth.users (id, email) values (?, ?)", id, username + "@test.local");
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, username);
        return id;
    }

    /** Inserts an event. Note the PostGIS argument order: (lng, lat). */
    private UUID event(String title, double lat, double lng, String approvalStatus, String status,
                       Instant startsAt, Instant endsAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into public.car_events
                    (id, event_type, title, description, location_name, starts_at, ends_at,
                     location, status, approval_status, created_by)
                values (?, 'car_meet', ?, 'Test description', 'Test location', ?, ?,
                        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?)
                """,
                id, title,
                java.sql.Timestamp.from(startsAt),
                endsAt == null ? null : java.sql.Timestamp.from(endsAt),
                lng, lat, status, approvalStatus, creator);
        return id;
    }

    /** An approved, upcoming event — the only kind the map is supposed to show. */
    private UUID visibleEvent(String title, double lat, double lng) {
        return event(title, lat, lng, "accepted", "upcoming",
                Instant.now().plus(2, ChronoUnit.DAYS), null);
    }

    private List<String> nearbyTitles(double radiusMetres, String categoryId) {
        return eventRepository.findVisibleNearby(CENTRE_LAT, CENTRE_LNG, radiusMetres, categoryId, 100)
                .stream()
                .map(MapEventRepository.MapPinRow::getTitle)
                .toList();
    }

    // ---- radius search ------------------------------------------------------

    @Test
    void returnsOnlyEventsInsideTheRadius() {
        visibleEvent("Centre meet", CENTRE_LAT, CENTRE_LNG);
        // ~11 km north of the centre: inside 25 km, outside 5 km.
        visibleEvent("Nearby meet", CENTRE_LAT + 0.1, CENTRE_LNG);
        // Bucharest, ~320 km away.
        visibleEvent("Far meet", 44.4268, 26.1025);

        assertThat(nearbyTitles(RADIUS_25_KM, null))
                .containsExactlyInAnyOrder("Centre meet", "Nearby meet");

        assertThat(nearbyTitles(5_000, null)).containsExactly("Centre meet");
    }

    @Test
    void reportsCoordinatesInTheRightOrder() {
        visibleEvent("Centre meet", CENTRE_LAT, CENTRE_LNG);

        var row = eventRepository.findVisibleNearby(CENTRE_LAT, CENTRE_LNG, RADIUS_25_KM, null, 100).getFirst();

        // Guards against a swapped lat/lng in the projection, which would still parse.
        assertThat(row.getLat()).isCloseTo(CENTRE_LAT, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(row.getLng()).isCloseTo(CENTRE_LNG, org.assertj.core.data.Offset.offset(0.0001));
    }

    // ---- visibility ---------------------------------------------------------

    @Test
    void hidesEventsThatAreNotApproved() {
        visibleEvent("Approved meet", CENTRE_LAT, CENTRE_LNG);
        event("Pending meet", CENTRE_LAT, CENTRE_LNG, "pending", "upcoming",
                Instant.now().plus(2, ChronoUnit.DAYS), null);
        event("Rejected meet", CENTRE_LAT, CENTRE_LNG, "rejected", "upcoming",
                Instant.now().plus(2, ChronoUnit.DAYS), null);

        assertThat(nearbyTitles(RADIUS_25_KM, null)).containsExactly("Approved meet");
    }

    @Test
    void hidesCancelledHiddenAndFinishedEvents() {
        visibleEvent("Live meet", CENTRE_LAT, CENTRE_LNG);
        Instant soon = Instant.now().plus(2, ChronoUnit.DAYS);
        event("Cancelled meet", CENTRE_LAT, CENTRE_LNG, "accepted", "canceled", soon, null);
        event("Hidden meet", CENTRE_LAT, CENTRE_LNG, "accepted", "hidden", soon, null);
        event("Finished meet", CENTRE_LAT, CENTRE_LNG, "accepted", "previous", soon, null);

        assertThat(nearbyTitles(RADIUS_25_KM, null)).containsExactly("Live meet");
    }

    /**
     * The time filter is the safety net for the deferred status sweep: nothing flips {@code status}
     * to {@code previous} automatically, so an event whose end has passed must drop off the map on
     * the strength of its timestamps alone.
     */
    @Test
    void hidesEventsWhoseEndHasPassedEvenWhileStatusStillSaysUpcoming() {
        event("Ended yesterday", CENTRE_LAT, CENTRE_LNG, "accepted", "upcoming",
                Instant.now().minus(3, ChronoUnit.DAYS),
                Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(nearbyTitles(RADIUS_25_KM, null)).isEmpty();
    }

    @Test
    void keepsARunningEventVisibleUntilItsEndTime() {
        event("Running now", CENTRE_LAT, CENTRE_LNG, "accepted", "upcoming",
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(3, ChronoUnit.HOURS));

        assertThat(nearbyTitles(RADIUS_25_KM, null)).containsExactly("Running now");
    }

    /** With no {@code ends_at}, an event lingers for 24 hours after it starts and then drops off. */
    @Test
    void anOpenEndedEventDropsOff24HoursAfterItStarts() {
        event("Started 2h ago", CENTRE_LAT, CENTRE_LNG, "accepted", "upcoming",
                Instant.now().minus(2, ChronoUnit.HOURS), null);
        event("Started 2 days ago", CENTRE_LAT, CENTRE_LNG, "accepted", "upcoming",
                Instant.now().minus(2, ChronoUnit.DAYS), null);

        assertThat(nearbyTitles(RADIUS_25_KM, null)).containsExactly("Started 2h ago");
    }

    // ---- category filter ----------------------------------------------------

    @Test
    void filtersByCategoryWhenOneIsGivenAndReturnsEverythingWhenNull() {
        visibleEvent("Centre meet", CENTRE_LAT, CENTRE_LNG);

        assertThat(nearbyTitles(RADIUS_25_KM, "car_meet")).containsExactly("Centre meet");
        assertThat(nearbyTitles(RADIUS_25_KM, "test_unavailable_category")).isEmpty();
        assertThat(nearbyTitles(RADIUS_25_KM, null)).containsExactly("Centre meet");
    }

    @Test
    void exposesTheCategoryLabelAlongsideItsId() {
        visibleEvent("Centre meet", CENTRE_LAT, CENTRE_LNG);

        var row = eventRepository.findVisibleNearby(CENTRE_LAT, CENTRE_LNG, RADIUS_25_KM, null, 100).getFirst();

        assertThat(row.getCategoryId()).isEqualTo("car_meet");
        assertThat(row.getCategoryLabel()).isEqualTo("Car meet");
    }

    // ---- review queue -------------------------------------------------------

    @Test
    void reviewQueueReturnsPendingEventsOldestFirst() {
        event("Second", CENTRE_LAT, CENTRE_LNG, "pending", "upcoming",
                Instant.now().plus(2, ChronoUnit.DAYS), null);
        // created_at is DB-managed, so force a distinguishable order rather than racing the clock.
        jdbc.update("update public.car_events set created_at = now() - interval '1 hour'");
        event("First", CENTRE_LAT, CENTRE_LNG, "pending", "upcoming",
                Instant.now().plus(2, ChronoUnit.DAYS), null);
        jdbc.update("update public.car_events set created_at = now() - interval '2 hours' where title = 'First'");

        assertThat(eventRepository.findByApprovalStatus("pending", null, null,
                        org.springframework.data.domain.Limit.of(10))
                .stream().map(e -> e.getTitle()).toList())
                .containsExactly("First", "Second");

        assertThat(eventRepository.countByApprovalStatus("pending")).isEqualTo(2);
    }

    // ---- schema constraints added by the hardening migration ----------------

    @Test
    void anEventCannotHaveTwoCreators() {
        UUID eventId = visibleEvent("Centre meet", CENTRE_LAT, CENTRE_LNG);
        UUID other = profile("second_" + UUID.randomUUID().toString().substring(0, 8));

        organizer(eventId, creator, null, "creator");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> organizer(eventId, other, null, "creator"));
    }

    @Test
    void aBusinessCannotBeTheCreator() {
        UUID eventId = visibleEvent("Centre meet", CENTRE_LAT, CENTRE_LNG);
        UUID businessId = business();

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> organizer(eventId, null, businessId, "creator"));
    }

    // One violation per test: the first failed statement aborts the surrounding transaction, so a
    // second insert in the same test would report 25P02 rather than the constraint under test.

    @Test
    void anOrganizerCannotBeBothAUserAndABusiness() {
        UUID eventId = visibleEvent("Centre meet", CENTRE_LAT, CENTRE_LNG);
        UUID businessId = business();

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> organizer(eventId, creator, businessId, "organizer"));
    }

    @Test
    void anOrganizerCannotBeNeitherAUserNorABusiness() {
        UUID eventId = visibleEvent("Centre meet", CENTRE_LAT, CENTRE_LNG);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> organizer(eventId, null, null, "organizer"));
    }

    @Test
    void theSameUserCannotOrganizeAnEventTwice() {
        UUID eventId = visibleEvent("Centre meet", CENTRE_LAT, CENTRE_LNG);
        organizer(eventId, creator, null, "organizer");

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> organizer(eventId, creator, null, "organizer"));
    }

    /** A map pin without coordinates is meaningless — the migration made the column NOT NULL. */
    @Test
    void anEventCannotBeStoredWithoutALocation() {
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> jdbc.update("""
                        insert into public.car_events
                            (id, event_type, title, description, location_name, starts_at,
                             status, approval_status, created_by)
                        values (?, 'car_meet', 'No location', 'd', 'l', now(), 'upcoming', 'pending', ?)
                        """, UUID.randomUUID(), creator));
    }

    private void organizer(UUID eventId, UUID userId, UUID businessId, String role) {
        jdbc.update("""
                insert into public.car_event_organizers
                    (id, event_id, individual_organizer_id, business_organizer_id, role)
                values (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), eventId, userId, businessId, role);
    }

    private UUID business() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into public.business_accounts
                    (id, name, type, address, location, city, verification_status, active_status, average_rating)
                values (?, 'Test Shop', 'test_business_type', 'Test address',
                        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                        'test-city', 'verified', 'active', 0)
                """, id, CENTRE_LNG, CENTRE_LAT);
        return id;
    }
}
