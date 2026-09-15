package com.tweakdapp.backend.forums.internal.repositories;

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
 * Real-SQL behaviors behind "notify a thread's / reply's author about a liker once, ever": the
 * notification ledgers say "notify" exactly once per (content, liker), across an unlike and a like
 * again, and are cleaned up with the content they point at.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ForumLikeNotificationLedgerIT extends AbstractPostgresIT {

    /** Seeded by {@code db/seed.sql} (a thread needs a real brand / model). */
    private static final UUID BRAND = UUID.fromString("00000000-0000-0000-0000-0000000b0001");
    private static final UUID MODEL = UUID.fromString("00000000-0000-0000-0000-0000000d0001");

    private static final UUID AUTHOR = UUID.fromString("00000000-0000-0000-0000-0000000ee201");
    private static final UUID LIKER = UUID.fromString("00000000-0000-0000-0000-0000000ee202");

    @Autowired
    private ForumThreadLikeRepository threadLikeRepository;
    @Autowired
    private ForumPostLikeRepository replyLikeRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private UUID thread;
    private UUID reply;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        createUser(AUTHOR);
        createUser(LIKER);
        thread = UUID.randomUUID();
        jdbc.update("""
                insert into public.forum_threads (id, user_id, title, content, brand_id, model_id)
                values (?, ?, ?, ?, ?, ?)
                """, thread, AUTHOR, "title", "body", BRAND, MODEL);
        reply = UUID.randomUUID();
        jdbc.update("insert into public.forum_thread_replies (id, thread_id, user_id, content) values (?, ?, ?, ?)",
                reply, thread, AUTHOR, "reply");
    }

    @Test
    void aThreadsAuthorIsNotifiedOnceEvenAcrossAnUnlikeAndALikeAgain() {
        assertThat(threadLikeRepository.insertIgnoringConflict(thread, LIKER)).isEqualTo(1);
        assertThat(threadLikeRepository.markLikeNotified(thread, LIKER)).isEqualTo(1);

        jdbc.update("delete from public.forum_thread_likes where thread_id = ? and user_id = ?", thread, LIKER);

        assertThat(threadLikeRepository.insertIgnoringConflict(thread, LIKER)).isEqualTo(1);
        assertThat(threadLikeRepository.markLikeNotified(thread, LIKER)).isEqualTo(0);
    }

    @Test
    void aRepliesAuthorIsNotifiedOnceEvenAcrossAnUnlikeAndALikeAgain() {
        assertThat(replyLikeRepository.insertIgnoringConflict(reply, LIKER)).isEqualTo(1);
        assertThat(replyLikeRepository.markLikeNotified(reply, LIKER)).isEqualTo(1);

        jdbc.update("delete from public.forum_post_likes where post_id = ? and user_id = ?", reply, LIKER);

        assertThat(replyLikeRepository.insertIgnoringConflict(reply, LIKER)).isEqualTo(1);
        assertThat(replyLikeRepository.markLikeNotified(reply, LIKER)).isEqualTo(0);
    }

    @Test
    void deletingTheThreadClearsBothLedgers() {
        threadLikeRepository.markLikeNotified(thread, LIKER);
        replyLikeRepository.markLikeNotified(reply, LIKER);

        // Replies cascade from their thread, and each ledger cascades from its content.
        jdbc.update("delete from public.forum_threads where id = ?", thread);

        assertThat(count("forum_thread_like_notifications", "thread_id", thread)).isZero();
        assertThat(count("forum_post_like_notifications", "post_id", reply)).isZero();
    }

    private int count(String table, String column, UUID id) {
        return jdbc.queryForObject(
                "select count(*) from public." + table + " where " + column + " = ?", Integer.class, id);
    }

    private void createUser(UUID id) {
        jdbc.update("insert into auth.users (id) values (?)", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, "u_" + id);
    }
}
