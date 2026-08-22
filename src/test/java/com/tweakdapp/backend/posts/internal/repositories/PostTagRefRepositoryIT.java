package com.tweakdapp.backend.posts.internal.repositories;

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
 * Real-SQL behaviors of the tag-ref keyset queries behind a profile's "tags" section: they find the
 * content a user (or their car) is tagged in, order it by when the tag was made rather than when
 * the content was written, exclude the user's own content, skip soft-deleted comments, and resume
 * exactly after a cursor.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostTagRefRepositoryIT extends AbstractPostgresIT {

    // Reference ids seeded in src/test/resources/db/seed.sql.
    private static final UUID BRAND = UUID.fromString("00000000-0000-0000-0000-0000000b0001");
    private static final UUID MODEL = UUID.fromString("00000000-0000-0000-0000-0000000d0001");

    private static final UUID TAGGED = UUID.fromString("00000000-0000-0000-0000-0000000aa001");
    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000bb001");

    private static final Instant T1 = Instant.parse("2026-07-20T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-07-22T10:00:00Z");

    @Autowired
    private TaggedPersonRepository taggedPersonRepository;
    @Autowired
    private TaggedCarRepository taggedCarRepository;
    @Autowired
    private CommentTaggedPersonRepository commentTaggedPersonRepository;
    @Autowired
    private CommentTaggedCarRepository commentTaggedCarRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        createUser(TAGGED);
        createUser(AUTHOR);
    }

    // ---- posts --------------------------------------------------------------

    @Test
    void findsPostsTheUserIsTaggedInNewestTagFirst() {
        UUID older = insertPost(AUTHOR, T1);
        UUID newer = insertPost(AUTHOR, T1);
        tagPerson(older, TAGGED, T1);
        tagPerson(newer, TAGGED, T3);

        List<TagRefRow> rows = taggedPersonRepository.findTaggedPostRefs(
                TAGGED, true, null, null, PageRequest.of(0, 10));

        assertThat(rows).extracting(TagRefRow::getTargetId).containsExactly(newer, older);
        assertThat(rows.getFirst().getTaggedAt()).isEqualTo(T3);
        assertThat(rows.getFirst().getParentId()).isNull();
    }

    @Test
    void ordersByTagTimeNotPostTime() {
        UUID oldPostTaggedToday = insertPost(AUTHOR, T1);
        UUID newPostTaggedLongAgo = insertPost(AUTHOR, T3);
        tagPerson(oldPostTaggedToday, TAGGED, T3);
        tagPerson(newPostTaggedLongAgo, TAGGED, T1);

        List<TagRefRow> rows = taggedPersonRepository.findTaggedPostRefs(
                TAGGED, true, null, null, PageRequest.of(0, 10));

        assertThat(rows).extracting(TagRefRow::getTargetId)
                .containsExactly(oldPostTaggedToday, newPostTaggedLongAgo);
    }

    @Test
    void skipsThePostsTheTaggedUserWroteThemselves() {
        UUID ownPost = insertPost(TAGGED, T1);
        tagPerson(ownPost, TAGGED, T1);

        assertThat(taggedPersonRepository.findTaggedPostRefs(
                TAGGED, true, null, null, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void resumesStrictlyAfterTheCursor() {
        UUID first = insertPost(AUTHOR, T1);
        UUID second = insertPost(AUTHOR, T1);
        tagPerson(first, TAGGED, T3);
        tagPerson(second, TAGGED, T2);

        List<TagRefRow> page = taggedPersonRepository.findTaggedPostRefs(
                TAGGED, false, T3, first, PageRequest.of(0, 10));

        assertThat(page).extracting(TagRefRow::getTargetId).containsExactly(second);
    }

    @Test
    void findsPostsTheUsersCarIsTaggedIn() {
        UUID car = insertCar(TAGGED);
        UUID post = insertPost(AUTHOR, T1);
        tagCar(post, car, T2);

        List<TagRefRow> rows = taggedCarRepository.findTaggedPostRefs(
                List.of(car), TAGGED, true, null, null, PageRequest.of(0, 10));

        assertThat(rows).extracting(TagRefRow::getTargetId).containsExactly(post);
        assertThat(rows.getFirst().getTaggedAt()).isEqualTo(T2);
    }

    @Test
    void fallsBackToThePostTimeWhenTheTagHasNoTimestamp() {
        UUID post = insertPost(AUTHOR, T2);
        jdbc.update("insert into public.tagged_people (post_id, user_id, created_at) values (?, ?, null)",
                post, TAGGED);

        List<TagRefRow> rows = taggedPersonRepository.findTaggedPostRefs(
                TAGGED, true, null, null, PageRequest.of(0, 10));

        assertThat(rows).singleElement()
                .extracting(TagRefRow::getTaggedAt).isEqualTo(T2);
    }

    @Test
    void untaggingRemovesOnlyThatUsersPersonTagAndTheirOwnCars() {
        UUID myCar = insertCar(TAGGED);
        UUID theirCar = insertCar(AUTHOR);
        UUID post = insertPost(AUTHOR, T1);
        tagPerson(post, TAGGED, T1);
        tagCar(post, myCar, T1);
        tagCar(post, theirCar, T1);

        taggedPersonRepository.deleteByIdPostIdAndIdUserId(post, TAGGED);
        taggedCarRepository.deleteByIdPostIdAndIdCarIdIn(post, List.of(myCar));

        assertThat(taggedPersonRepository.findAllByIdPostId(post)).isEmpty();
        assertThat(taggedCarRepository.findAllByIdPostId(post))
                .extracting(t -> t.getId().getCarId()).containsExactly(theirCar);
    }

    // ---- comments -----------------------------------------------------------

    @Test
    void findsCommentsTheUserIsTaggedInWithTheirParentPost() {
        UUID post = insertPost(AUTHOR, T1);
        UUID comment = insertComment(post, AUTHOR, T1, false);
        tagPersonInComment(comment, TAGGED, T2);

        List<TagRefRow> rows = commentTaggedPersonRepository.findTaggedCommentRefs(
                TAGGED, true, null, null, PageRequest.of(0, 10));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getTargetId()).isEqualTo(comment);
            assertThat(row.getParentId()).isEqualTo(post);
            assertThat(row.getTaggedAt()).isEqualTo(T2);
        });
    }

    @Test
    void skipsSoftDeletedCommentsAndTheUsersOwnComments() {
        UUID post = insertPost(AUTHOR, T1);
        UUID deleted = insertComment(post, AUTHOR, T1, true);
        UUID ownComment = insertComment(post, TAGGED, T1, false);
        tagPersonInComment(deleted, TAGGED, T2);
        tagPersonInComment(ownComment, TAGGED, T2);

        assertThat(commentTaggedPersonRepository.findTaggedCommentRefs(
                TAGGED, true, null, null, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void findsCommentsTheUsersCarIsTaggedIn() {
        UUID car = insertCar(TAGGED);
        UUID post = insertPost(AUTHOR, T1);
        UUID comment = insertComment(post, AUTHOR, T1, false);
        tagCarInComment(comment, car, T3);

        List<TagRefRow> rows = commentTaggedCarRepository.findTaggedCommentRefs(
                List.of(car), TAGGED, true, null, null, PageRequest.of(0, 10));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getTargetId()).isEqualTo(comment);
            assertThat(row.getParentId()).isEqualTo(post);
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

    private UUID insertPost(UUID author, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.posts (id, user_id, description, created_at) values (?, ?, ?, ?)",
                id, author, "post", at(createdAt));
        return id;
    }

    private UUID insertComment(UUID postId, UUID author, Instant createdAt, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into public.comments (id, post_id, user_id, content, created_at, is_deleted) "
                        + "values (?, ?, ?, ?, ?, ?)",
                id, postId, author, "comment", at(createdAt), deleted);
        return id;
    }

    private void tagPerson(UUID postId, UUID userId, Instant taggedAt) {
        jdbc.update("insert into public.tagged_people (post_id, user_id, created_at) values (?, ?, ?)",
                postId, userId, at(taggedAt));
    }

    private void tagCar(UUID postId, UUID carId, Instant taggedAt) {
        jdbc.update("insert into public.tagged_cars (post_id, car_id, created_at) values (?, ?, ?)",
                postId, carId, at(taggedAt));
    }

    private void tagPersonInComment(UUID commentId, UUID userId, Instant taggedAt) {
        jdbc.update("insert into public.comment_tagged_people (comment_id, user_id, created_at) values (?, ?, ?)",
                commentId, userId, at(taggedAt));
    }

    private void tagCarInComment(UUID commentId, UUID carId, Instant taggedAt) {
        jdbc.update("insert into public.comment_tagged_cars (comment_id, car_id, created_at) values (?, ?, ?)",
                commentId, carId, at(taggedAt));
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
