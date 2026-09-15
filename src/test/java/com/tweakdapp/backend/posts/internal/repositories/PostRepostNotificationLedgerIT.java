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
 * Real-SQL behaviors behind "notify a post's author about a reposter once, ever": the repost insert
 * is idempotent and keeps {@code shares_count} right through the real trigger, and the notification
 * ledger says "notify" exactly once per (post, reposter) — including across an undo and a repost
 * again, which deletes and re-inserts the repost row itself.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostRepostNotificationLedgerIT extends AbstractPostgresIT {

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000ee001");
    private static final UUID REPOSTER = UUID.fromString("00000000-0000-0000-0000-0000000ee002");

    @Autowired
    private PostShareRepository postShareRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private UUID post;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        createUser(AUTHOR);
        createUser(REPOSTER);
        post = UUID.randomUUID();
        jdbc.update("insert into public.posts (id, user_id, description) values (?, ?, ?)", post, AUTHOR, "post");
    }

    @Test
    void aRepostIsInsertedOnceAndARepeatIsANoOp() {
        assertThat(postShareRepository.insertIgnoringConflict(post, REPOSTER)).isEqualTo(1);
        assertThat(postShareRepository.insertIgnoringConflict(post, REPOSTER)).isEqualTo(0);

        assertThat(sharesCount()).isEqualTo(1);
    }

    @Test
    void theAuthorIsNotifiedOnceEvenAcrossAnUndoAndARepostAgain() {
        postShareRepository.insertIgnoringConflict(post, REPOSTER);
        assertThat(postShareRepository.markRepostNotified(post, REPOSTER)).isEqualTo(1);

        undoRepost();
        assertThat(sharesCount()).isZero();

        assertThat(postShareRepository.insertIgnoringConflict(post, REPOSTER)).isEqualTo(1);
        assertThat(postShareRepository.markRepostNotified(post, REPOSTER)).isEqualTo(0);
        assertThat(sharesCount()).isEqualTo(1);
    }

    @Test
    void deletingThePostClearsItsLedgerRows() {
        postShareRepository.insertIgnoringConflict(post, REPOSTER);
        postShareRepository.markRepostNotified(post, REPOSTER);

        jdbc.update("delete from public.posts where id = ?", post);

        assertThat(jdbc.queryForObject(
                "select count(*) from public.post_repost_notifications where post_id = ?", Integer.class, post))
                .isZero();
    }

    // ---- helpers ------------------------------------------------------------

    private void undoRepost() {
        jdbc.update("delete from public.post_shares where post_id = ? and user_id = ?", post, REPOSTER);
    }

    private int sharesCount() {
        return jdbc.queryForObject("select shares_count from public.posts where id = ?", Integer.class, post);
    }

    private void createUser(UUID id) {
        jdbc.update("insert into auth.users (id) values (?)", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, "u_" + id);
    }
}
