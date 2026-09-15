package com.tweakdapp.backend.posts.internal.repositories;

import com.tweakdapp.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-SQL behaviors behind "notify a post's author about a liker once, ever": the like insert is
 * idempotent, and the notification ledger says "notify" exactly once per (post, liker) — including
 * across an unlike and a like again, which deletes and re-inserts the like row itself.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostLikeNotificationLedgerIT extends AbstractPostgresIT {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000ee101");
    private static final UUID LIKER = UUID.fromString("00000000-0000-0000-0000-0000000ee102");

    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private UUID post;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        createUser(AUTHOR);
        createUser(LIKER);
        post = UUID.randomUUID();
        jdbc.update("insert into public.posts (id, user_id, description) values (?, ?, ?)", post, AUTHOR, "post");
    }

    @Test
    void aLikeIsInsertedOnceAndARepeatIsANoOp() {
        assertThat(postLikeRepository.insertIgnoringConflict(post, LIKER)).isEqualTo(1);
        assertThat(postLikeRepository.insertIgnoringConflict(post, LIKER)).isEqualTo(0);

        assertThat(likeRows()).isEqualTo(1);
    }

    @Test
    void theAuthorIsNotifiedOnceEvenAcrossAnUnlikeAndALikeAgain() {
        postLikeRepository.insertIgnoringConflict(post, LIKER);
        assertThat(postLikeRepository.markLikeNotified(post, LIKER)).isEqualTo(1);

        jdbc.update("delete from public.post_likes where post_id = ? and user_id = ?", post, LIKER);

        assertThat(postLikeRepository.insertIgnoringConflict(post, LIKER)).isEqualTo(1);
        assertThat(postLikeRepository.markLikeNotified(post, LIKER)).isEqualTo(0);
    }

    @Test
    void deletingThePostClearsItsLedgerRows() {
        postLikeRepository.insertIgnoringConflict(post, LIKER);
        postLikeRepository.markLikeNotified(post, LIKER);

        jdbc.update("delete from public.posts where id = ?", post);

        assertThat(jdbc.queryForObject(
                "select count(*) from public.post_like_notifications where post_id = ?", Integer.class, post))
                .isZero();
    }

    private int likeRows() {
        return jdbc.queryForObject(
                "select count(*) from public.post_likes where post_id = ? and user_id = ?", Integer.class, post, LIKER);
    }

    private void createUser(UUID id) {
        jdbc.update("insert into auth.users (id) values (?)", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, "u_" + id);
    }
}
