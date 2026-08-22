package com.tweakdapp.backend.forums.internal.repositories;

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
 * Real-SQL behaviors of the forums half of a profile's "tags" section: threads and replies the user
 * (or their car) is tagged in, ordered by when the tag was made, excluding their own content and
 * soft-deleted replies — while anonymized threads, which keep their title and body, stay in.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ForumTagRefRepositoryIT extends AbstractPostgresIT {

    // Reference ids seeded in src/test/resources/db/seed.sql.
    private static final UUID BRAND = UUID.fromString("00000000-0000-0000-0000-0000000b0001");
    private static final UUID MODEL = UUID.fromString("00000000-0000-0000-0000-0000000d0001");

    private static final UUID TAGGED = UUID.fromString("00000000-0000-0000-0000-0000000cc001");
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000dd001");

    private static final Instant T1 = Instant.parse("2026-07-20T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-07-22T10:00:00Z");

    @Autowired
    private ForumThreadTaggedPersonRepository threadTaggedPersonRepository;
    @Autowired
    private ForumThreadTaggedCarRepository threadTaggedCarRepository;
    @Autowired
    private ForumReplyTaggedPersonRepository replyTaggedPersonRepository;
    @Autowired
    private ForumReplyTaggedCarRepository replyTaggedCarRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        createUser(TAGGED);
        createUser(AUTHOR);
    }

    // ---- threads ------------------------------------------------------------

    @Test
    void findsThreadsTheUserIsTaggedInNewestTagFirst() {
        UUID older = insertThread(AUTHOR, T1, false);
        UUID newer = insertThread(AUTHOR, T1, false);
        tagPersonInThread(older, TAGGED, T1);
        tagPersonInThread(newer, TAGGED, T3);

        List<TagRefRow> rows = threadTaggedPersonRepository.findTaggedThreadRefs(
                TAGGED, true, null, null, PageRequest.of(0, 10));

        assertThat(rows).extracting(TagRefRow::getTargetId).containsExactly(newer, older);
        assertThat(rows.getFirst().getParentId()).isNull();
    }

    @Test
    void keepsAnonymizedThreadsButSkipsTheUsersOwn() {
        UUID anonymized = insertThread(AUTHOR, T1, true);
        UUID ownThread = insertThread(TAGGED, T1, false);
        tagPersonInThread(anonymized, TAGGED, T2);
        tagPersonInThread(ownThread, TAGGED, T2);

        List<TagRefRow> rows = threadTaggedPersonRepository.findTaggedThreadRefs(
                TAGGED, true, null, null, PageRequest.of(0, 10));

        assertThat(rows).extracting(TagRefRow::getTargetId).containsExactly(anonymized);
    }

    @Test
    void resumesStrictlyAfterTheCursor() {
        UUID first = insertThread(AUTHOR, T1, false);
        UUID second = insertThread(AUTHOR, T1, false);
        tagPersonInThread(first, TAGGED, T3);
        tagPersonInThread(second, TAGGED, T2);

        List<TagRefRow> page = threadTaggedPersonRepository.findTaggedThreadRefs(
                TAGGED, false, T3, first, PageRequest.of(0, 10));

        assertThat(page).extracting(TagRefRow::getTargetId).containsExactly(second);
    }

    @Test
    void findsThreadsTheUsersCarIsTaggedIn() {
        UUID car = insertCar(TAGGED);
        UUID thread = insertThread(AUTHOR, T1, false);
        tagCarInThread(thread, car, T2);

        List<TagRefRow> rows = threadTaggedCarRepository.findTaggedThreadRefs(
                List.of(car), TAGGED, true, null, null, PageRequest.of(0, 10));

        assertThat(rows).extracting(TagRefRow::getTargetId).containsExactly(thread);
    }

    @Test
    void untaggingRemovesOnlyThatUsersPersonTagAndTheirOwnCars() {
        UUID myCar = insertCar(TAGGED);
        UUID theirCar = insertCar(AUTHOR);
        UUID thread = insertThread(AUTHOR, T1, false);
        tagPersonInThread(thread, TAGGED, T1);
        tagCarInThread(thread, myCar, T1);
        tagCarInThread(thread, theirCar, T1);

        threadTaggedPersonRepository.deleteByIdThreadIdAndIdUserId(thread, TAGGED);
        threadTaggedCarRepository.deleteByIdThreadIdAndIdCarIdIn(thread, List.of(myCar));

        assertThat(threadTaggedPersonRepository.findAllByIdThreadId(thread)).isEmpty();
        assertThat(threadTaggedCarRepository.findAllByIdThreadId(thread))
                .extracting(t -> t.getId().getCarId()).containsExactly(theirCar);
    }

    // ---- replies ------------------------------------------------------------

    @Test
    void findsRepliesTheUserIsTaggedInWithTheirParentThread() {
        UUID thread = insertThread(AUTHOR, T1, false);
        UUID reply = insertReply(thread, AUTHOR, T1, false);
        tagPersonInReply(reply, TAGGED, T3);

        List<TagRefRow> rows = replyTaggedPersonRepository.findTaggedReplyRefs(
                TAGGED, true, null, null, PageRequest.of(0, 10));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getTargetId()).isEqualTo(reply);
            assertThat(row.getParentId()).isEqualTo(thread);
            assertThat(row.getTaggedAt()).isEqualTo(T3);
        });
    }

    @Test
    void skipsSoftDeletedRepliesAndTheUsersOwnReplies() {
        UUID thread = insertThread(AUTHOR, T1, false);
        UUID deleted = insertReply(thread, AUTHOR, T1, true);
        UUID ownReply = insertReply(thread, TAGGED, T1, false);
        tagPersonInReply(deleted, TAGGED, T2);
        tagPersonInReply(ownReply, TAGGED, T2);

        assertThat(replyTaggedPersonRepository.findTaggedReplyRefs(
                TAGGED, true, null, null, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void findsRepliesTheUsersCarIsTaggedIn() {
        UUID car = insertCar(TAGGED);
        UUID thread = insertThread(AUTHOR, T1, false);
        UUID reply = insertReply(thread, AUTHOR, T1, false);
        tagCarInReply(reply, car, T2);

        List<TagRefRow> rows = replyTaggedCarRepository.findTaggedReplyRefs(
                List.of(car), TAGGED, true, null, null, PageRequest.of(0, 10));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getTargetId()).isEqualTo(reply);
            assertThat(row.getParentId()).isEqualTo(thread);
        });
    }

    // ---- fixture helpers ----------------------------------------------------

    private void createUser(UUID id) {
        jdbc.update("insert into auth.users (id) values (?)", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, "u_" + id);
    }

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

    private UUID insertThread(UUID author, Instant createdAt, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into public.forum_threads
                    (id, user_id, title, content, brand_id, model_id, created_at, is_deleted)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, author, "title", "body", BRAND, MODEL, at(createdAt), deleted);
        return id;
    }

    private UUID insertReply(UUID threadId, UUID author, Instant createdAt, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into public.forum_thread_replies
                    (id, thread_id, user_id, content, created_at, is_deleted)
                values (?, ?, ?, ?, ?, ?)
                """, id, threadId, author, "reply", at(createdAt), deleted);
        return id;
    }

    private void tagPersonInThread(UUID threadId, UUID userId, Instant taggedAt) {
        jdbc.update("insert into public.forum_thread_tagged_people (thread_id, user_id, created_at) "
                + "values (?, ?, ?)", threadId, userId, at(taggedAt));
    }

    private void tagCarInThread(UUID threadId, UUID carId, Instant taggedAt) {
        jdbc.update("insert into public.forum_thread_tagged_cars (thread_id, car_id, created_at) "
                + "values (?, ?, ?)", threadId, carId, at(taggedAt));
    }

    private void tagPersonInReply(UUID replyId, UUID userId, Instant taggedAt) {
        jdbc.update("insert into public.forum_thread_reply_tagged_people (reply_id, user_id, created_at) "
                + "values (?, ?, ?)", replyId, userId, at(taggedAt));
    }

    private void tagCarInReply(UUID replyId, UUID carId, Instant taggedAt) {
        jdbc.update("insert into public.forum_thread_reply_tagged_cars (reply_id, car_id, created_at) "
                + "values (?, ?, ?)", replyId, carId, at(taggedAt));
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
