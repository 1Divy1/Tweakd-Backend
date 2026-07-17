package com.carsocialmedia.backend.dms.internal.repositories;

import com.carsocialmedia.backend.dms.internal.entities.DmMessageCarTagEntity;
import com.carsocialmedia.backend.dms.internal.entities.DmMessageCarTagId;
import com.carsocialmedia.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-SQL behaviors of {@link DmMessageCarTagRepository} against the Supabase schema: the composite
 * ({@code message_id}, {@code car_id}) key persists and reads back, the batch
 * {@code findAllByIdMessageIdIn} groups a whole page's tags in one query, and both foreign keys are
 * ON DELETE CASCADE — deleting the car silently removes the tag (the "a deleted car disappears from
 * old messages" behavior) and deleting the message removes it too.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DmMessageCarTagRepositoryIT extends AbstractPostgresIT {

    // Reference ids seeded in src/test/resources/db/seed.sql.
    private static final UUID BRAND = UUID.fromString("00000000-0000-0000-0000-0000000b0001");
    private static final UUID MODEL = UUID.fromString("00000000-0000-0000-0000-0000000d0001");

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private static final UUID PEER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final Instant T1 = Instant.parse("2026-07-16T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-16T09:00:00Z");

    @Autowired
    private DmMessageCarTagRepository carTagRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        createUser(OWNER);
        createUser(PEER);
    }

    // ---- fixture helpers ----------------------------------------------------

    private void createUser(UUID id) {
        jdbc.update("insert into auth.users (id) values (?)", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, "u_" + id);
    }

    /** Inserts a car in the owner's (trigger-created) garage using the seeded reference rows. */
    private UUID insertCar(UUID owner) {
        UUID carId = UUID.randomUUID();
        UUID garageId = jdbc.queryForObject(
                "select id from public.garages where owner_id = ?", UUID.class, owner);
        jdbc.update("""
                insert into public.cars
                    (id, garage_id, brand_id, model_id, drivetrain_id, year, horsepower, torque,
                     weight, engine_displacement, color_id, mileage_unit_id, status_id, fuel_id)
                values (?, ?, ?, ?, 'test_dt', 2020, 300, 400, 1500, 2.0, 'test_color', 'test_km',
                        'test_status', 'test_fuel')
                """, carId, garageId, BRAND, MODEL);
        return carId;
    }

    private UUID insertConversation() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.dm_conversations (id, user_a, user_b) values (?, ?, ?)",
                id, OWNER, PEER);
        return id;
    }

    private UUID insertMessage(UUID conversationId, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.dm_messages (id, conversation_id, sender_id, content, created_at) "
                        + "values (?, ?, ?, ?, ?)",
                id, conversationId, OWNER, "hi", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        return id;
    }

    private void tag(UUID messageId, UUID carId) {
        DmMessageCarTagEntity t = new DmMessageCarTagEntity();
        t.setId(new DmMessageCarTagId(messageId, carId));
        carTagRepository.saveAndFlush(t);
    }

    // ---- tests --------------------------------------------------------------

    @Test
    void batchQueryGroupsTagsForAWholePageOfMessages() {
        UUID conv = insertConversation();
        UUID m1 = insertMessage(conv, T1);
        UUID m2 = insertMessage(conv, T2);
        UUID car1 = insertCar(OWNER);
        UUID car2 = insertCar(PEER); // another user's car may be tagged
        UUID car3 = insertCar(OWNER);
        tag(m1, car1);
        tag(m1, car2);
        tag(m2, car3);

        List<DmMessageCarTagEntity> rows = carTagRepository.findAllByIdMessageIdIn(List.of(m1, m2));

        assertThat(rows).hasSize(3);
        assertThat(rows).filteredOn(r -> r.getId().getMessageId().equals(m1))
                .extracting(r -> r.getId().getCarId())
                .containsExactlyInAnyOrder(car1, car2);
        assertThat(rows).filteredOn(r -> r.getId().getMessageId().equals(m2))
                .extracting(r -> r.getId().getCarId())
                .containsExactly(car3);
    }

    @Test
    void deletingTheCarCascadesAndRemovesTheTag() {
        UUID conv = insertConversation();
        UUID m1 = insertMessage(conv, T1);
        UUID car1 = insertCar(OWNER);
        tag(m1, car1);
        assertThat(carTagRepository.findAllByIdMessageIdIn(List.of(m1))).hasSize(1);

        jdbc.update("delete from public.cars where id = ?", car1);

        assertThat(carTagRepository.findAllByIdMessageIdIn(List.of(m1))).isEmpty();
    }

    @Test
    void deletingTheMessageCascadesAndRemovesTheTag() {
        UUID conv = insertConversation();
        UUID m1 = insertMessage(conv, T1);
        UUID car1 = insertCar(OWNER);
        tag(m1, car1);
        assertThat(carTagRepository.findAllByIdMessageIdIn(List.of(m1))).hasSize(1);

        jdbc.update("delete from public.dm_messages where id = ?", m1);

        assertThat(carTagRepository.findAllByIdMessageIdIn(List.of(m1))).isEmpty();
    }
}
