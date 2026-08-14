package com.carsocialmedia.backend.business.internal.repositories;

import com.carsocialmedia.backend.business.internal.entities.BusinessAccountEntity;
import com.carsocialmedia.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Real-SQL behaviour of the business tables in a PostGIS container: the {@code ST_DWithin} radius
 * search, the active+verified visibility filter, and the schema constraints the hardening
 * migration added (one hours row per weekday, hours present iff the day is open).
 *
 * <p>The radius query is native PostGIS with an interface projection, so it is only ever verified
 * by running it — a mistyped alias or a swapped lat/lng would compile perfectly well.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BusinessAccountRepositoryIT extends AbstractPostgresIT {

    /** Cluj-Napoca city centre — the search origin for every case below. */
    private static final double CENTRE_LAT = 46.7712;
    private static final double CENTRE_LNG = 23.6236;

    @Autowired
    private BusinessAccountRepository businessRepository;
    @Autowired
    private BusinessHoursRepository hoursRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from public.business_hours");
        jdbc.update("delete from public.business_accounts");
    }

    // ---- fixture helpers ----------------------------------------------------

    /** Inserts a business at the given coordinates. Note the PostGIS argument order: (lng, lat). */
    private UUID business(String name, double lat, double lng, String verification, String active) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into public.business_accounts
                    (id, name, type, address, location, city, verification_status, active_status, average_rating)
                values (?, ?, 'test_business_type', 'Test address',
                        ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                        'test-city', ?, ?, 0)
                """, id, name, lng, lat, verification, active);
        return id;
    }

    private UUID visibleBusiness(String name, double lat, double lng) {
        return business(name, lat, lng, "verified", "active");
    }

    private List<String> nearbyNames(double radiusKm, String typeId, int limit) {
        return businessRepository
                .findVisibleNearby(CENTRE_LAT, CENTRE_LNG, radiusKm * 1000.0, typeId, limit)
                .stream()
                .map(BusinessAccountRepository.MapPinRow::getName)
                .toList();
    }

    // ---- radius search -------------------------------------------------------

    @Test
    void returnsOnlyBusinessesInsideTheRadius() {
        visibleBusiness("Near", CENTRE_LAT, CENTRE_LNG);
        // Bucharest — roughly 320 km from Cluj-Napoca.
        visibleBusiness("Far", 44.4268, 26.1025);

        assertThat(nearbyNames(50, null, 100)).containsExactly("Near");
        assertThat(nearbyNames(400, null, 100)).containsExactlyInAnyOrder("Near", "Far");
    }

    @Test
    void reportsCoordinatesAndTypeInTheRightShape() {
        visibleBusiness("Here", CENTRE_LAT, CENTRE_LNG);

        var row = businessRepository
                .findVisibleNearby(CENTRE_LAT, CENTRE_LNG, 50_000, null, 100)
                .getFirst();

        // Guards the classic PostGIS bug: lat and lng swapped on the way out.
        assertThat(row.getLat()).isCloseTo(CENTRE_LAT, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(row.getLng()).isCloseTo(CENTRE_LNG, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(row.getTypeId()).isEqualTo("test_business_type");
        assertThat(row.getTypeLabel()).isEqualTo("Test Business Type");
    }

    @Test
    void filtersByBusinessType() {
        visibleBusiness("Typed", CENTRE_LAT, CENTRE_LNG);

        assertThat(nearbyNames(50, "test_business_type", 100)).containsExactly("Typed");
        assertThat(nearbyNames(50, "test_other_type", 100)).isEmpty();
        // A null type must not filter anything out — the `cast(:typeId as text)` branch.
        assertThat(nearbyNames(50, null, 100)).containsExactly("Typed");
    }

    @Test
    void honoursTheResultLimit() {
        visibleBusiness("A", CENTRE_LAT, CENTRE_LNG);
        visibleBusiness("B", CENTRE_LAT + 0.01, CENTRE_LNG);
        visibleBusiness("C", CENTRE_LAT + 0.02, CENTRE_LNG);

        assertThat(nearbyNames(50, null, 2)).hasSize(2);
    }

    // ---- visibility ----------------------------------------------------------

    @Test
    void hidesBusinessesThatAreNotActiveAndVerified() {
        visibleBusiness("Visible", CENTRE_LAT, CENTRE_LNG);
        business("Pending", CENTRE_LAT, CENTRE_LNG, "pending", "active");
        business("Rejected", CENTRE_LAT, CENTRE_LNG, "rejected", "active");
        business("Suspended", CENTRE_LAT, CENTRE_LNG, "verified", "suspended");
        business("Deleted", CENTRE_LAT, CENTRE_LNG, "verified", "deleted");

        assertThat(nearbyNames(50, null, 100)).containsExactly("Visible");
    }

    @Test
    void findVisibleByIdAppliesTheSameFilter() {
        UUID visible = visibleBusiness("Visible", CENTRE_LAT, CENTRE_LNG);
        UUID suspended = business("Suspended", CENTRE_LAT, CENTRE_LNG, "verified", "suspended");

        assertThat(businessRepository.findVisibleById(visible)).isPresent();
        assertThat(businessRepository.findVisibleById(suspended)).isEmpty();
        assertThat(businessRepository.findVisibleById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void visibleBusinessExposesItsTypeAndDefaultTimezone() {
        UUID id = visibleBusiness("Visible", CENTRE_LAT, CENTRE_LNG);

        BusinessAccountEntity business = businessRepository.findVisibleById(id).orElseThrow();

        assertThat(business.isVisible()).isTrue();
        assertThat(business.getType().getType()).isEqualTo("Test Business Type");
        assertThat(business.getTimezone()).isEqualTo("Europe/Bucharest");
        assertThat(business.getCityId()).isEqualTo("test-city");
    }

    // ---- business_hours constraints -----------------------------------------

    @Test
    void allowsOneRowPerWeekdayAndRejectsADuplicateWeekday() {
        UUID id = visibleBusiness("Visible", CENTRE_LAT, CENTRE_LNG);
        insertHours(id, 1, "09:00", "18:00");
        insertHours(id, 2, "09:00", "18:00");

        assertThat(hoursRepository.findByBusinessIdOrderByWeekdayAsc(id)).hasSize(2);

        // The pre-migration UNIQUE(business_id) would have rejected the second insert above;
        // UNIQUE(business_id, weekday) rejects only a repeated weekday.
        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertHours(id, 1, "10:00", "19:00"));
    }

    // Each of the four cases below gets its own test: a constraint violation aborts the
    // surrounding transaction, so a second failing statement would report 25P02
    // ("current transaction is aborted") rather than the constraint under test.

    @Test
    void rejectsAWeekdayBelowTheIsoRange() {
        UUID id = visibleBusiness("Visible", CENTRE_LAT, CENTRE_LNG);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertHours(id, 0, "09:00", "18:00"));
    }

    @Test
    void rejectsAWeekdayAboveTheIsoRange() {
        UUID id = visibleBusiness("Visible", CENTRE_LAT, CENTRE_LNG);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> insertHours(id, 8, "09:00", "18:00"));
    }

    @Test
    void rejectsAnOpenDayWithoutHours() {
        UUID id = visibleBusiness("Visible", CENTRE_LAT, CENTRE_LNG);

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbc.update("insert into public.business_hours (business_id, weekday, is_closed) values (?, 1, false)", id));
    }

    @Test
    void rejectsAClosedDayThatStillCarriesHours() {
        UUID id = visibleBusiness("Visible", CENTRE_LAT, CENTRE_LNG);

        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                jdbc.update("""
                        insert into public.business_hours (business_id, weekday, is_closed, opening_hour, closing_hour)
                        values (?, 2, true, '09:00', '18:00')
                        """, id));
    }

    @Test
    void deletingABusinessCascadesToItsHours() {
        UUID id = visibleBusiness("Visible", CENTRE_LAT, CENTRE_LNG);
        insertHours(id, 1, "09:00", "18:00");

        jdbc.update("delete from public.business_accounts where id = ?", id);

        assertThat(hoursRepository.findByBusinessIdOrderByWeekdayAsc(id)).isEmpty();
    }

    @Test
    void loadsHoursForSeveralBusinessesInOneQuery() {
        UUID first = visibleBusiness("First", CENTRE_LAT, CENTRE_LNG);
        UUID second = visibleBusiness("Second", CENTRE_LAT + 0.01, CENTRE_LNG);
        insertHours(first, 1, "09:00", "18:00");
        insertHours(second, 1, "10:00", "20:00");

        assertThat(hoursRepository.findByBusinessIdIn(List.of(first, second))).hasSize(2);
    }

    private void insertHours(UUID businessId, int weekday, String from, String to) {
        jdbc.update("""
                insert into public.business_hours (business_id, weekday, opening_hour, closing_hour, is_closed)
                values (?, ?, ?::time, ?::time, false)
                """, businessId, weekday, from, to);
    }
}
