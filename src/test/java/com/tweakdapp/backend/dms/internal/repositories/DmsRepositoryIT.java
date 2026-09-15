package com.tweakdapp.backend.dms.internal.repositories;

import com.tweakdapp.backend.dms.internal.entities.DmConversationEntity;
import com.tweakdapp.backend.dms.internal.entities.DmMessageEntity;
import com.tweakdapp.backend.dms.internal.entities.DmParticipantStateEntity;
import com.tweakdapp.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import com.tweakdapp.backend.shared.blocking.BlockDirectory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-SQL behaviors of the DM repositories against the Supabase schema in a PostGIS container: the
 * chats-list keyset filter/order (hidden + message-less conversations excluded, newest first) and
 * its continuation, the presence peer query, the native ON CONFLICT upserts (find-or-create
 * idempotency), the message-history keyset + sender-scoped fetch, and participant-state mutations
 * (unread bump/unhide, unread aggregate).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DmsRepositoryIT extends AbstractPostgresIT {

    private static final UUID U = UUID.fromString("00000000-0000-0000-0000-0000000000a0");
    private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID P3 = UUID.fromString("00000000-0000-0000-0000-0000000000b3");
    private static final UUID P4 = UUID.fromString("00000000-0000-0000-0000-0000000000b4");

    private static final Instant T1 = Instant.parse("2026-07-16T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-16T09:00:00Z");
    private static final Instant T3 = Instant.parse("2026-07-16T10:00:00Z");

    @Autowired
    private DmConversationRepository conversationRepository;
    @Autowired
    private DmParticipantStateRepository stateRepository;
    @Autowired
    private DmMessageRepository messageRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        for (UUID id : List.of(U, P1, P2, P3, P4)) {
            createUser(id);
        }
    }

    // ---- fixture helpers ----------------------------------------------------

    private void createUser(UUID id) {
        jdbc.update("insert into auth.users (id) values (?)", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, "u_" + id);
    }

    private void insertConversation(UUID id, UUID userA, UUID userB, Instant lastMessageAt) {
        jdbc.update("insert into public.dm_conversations (id, user_a, user_b, last_message_at) values (?, ?, ?, ?)",
                id, userA, userB, lastMessageAt == null ? null : OffsetDateTime.ofInstant(lastMessageAt, ZoneOffset.UTC));
    }

    private void insertState(UUID conversationId, UUID userId, int unread, Instant hiddenAt) {
        jdbc.update("insert into public.dm_participant_state (conversation_id, user_id, unread_count, hidden_at) "
                        + "values (?, ?, ?, ?)",
                conversationId, userId, unread, hiddenAt == null ? null : OffsetDateTime.ofInstant(hiddenAt, ZoneOffset.UTC));
    }

    private void insertMessage(UUID id, UUID conversationId, UUID senderId, Instant createdAt) {
        jdbc.update("insert into public.dm_messages (id, conversation_id, sender_id, content, created_at) "
                        + "values (?, ?, ?, ?, ?)",
                id, conversationId, senderId, "hi", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
    }

    // ---- chats-list keyset --------------------------------------------------

    @Test
    void firstPageExcludesHiddenAndMessagelessConversationsNewestFirst() {
        UUID conv1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1"); // newest, visible
        UUID conv2 = UUID.fromString("00000000-0000-0000-0000-0000000000c2"); // older, visible
        UUID conv3 = UUID.fromString("00000000-0000-0000-0000-0000000000c3"); // hidden by U
        UUID conv4 = UUID.fromString("00000000-0000-0000-0000-0000000000c4"); // no message yet
        insertConversation(conv1, U, P1, T3);
        insertConversation(conv2, U, P2, T2);
        insertConversation(conv3, U, P3, T1);
        insertConversation(conv4, U, P4, null);
        insertState(conv1, U, 0, null);
        insertState(conv2, U, 0, null);
        insertState(conv3, U, 0, T1); // hidden
        insertState(conv4, U, 0, null);

        List<DmConversationEntity> page = conversationRepository.findFirstPage(
                U, List.of(BlockDirectory.NOBODY), PageRequest.of(0, 10));

        assertThat(page).extracting(DmConversationEntity::getId).containsExactly(conv1, conv2);
    }

    @Test
    void findFirstPageLeavesOutConversationsWithAPeerHiddenByABlock() {
        UUID conv1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID conv2 = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        insertConversation(conv1, U, P1, T3);
        insertConversation(conv2, U, P2, T2);
        insertState(conv1, U, 0, null);
        insertState(conv2, U, 0, null);

        List<DmConversationEntity> page = conversationRepository.findFirstPage(U, List.of(P1), PageRequest.of(0, 10));

        assertThat(page).extracting(DmConversationEntity::getId).containsExactly(conv2);
    }

    @Test
    void findPageAfterContinuesStrictlyAfterTheCursorRow() {
        UUID conv1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID conv2 = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        insertConversation(conv1, U, P1, T3);
        insertConversation(conv2, U, P2, T2);
        insertState(conv1, U, 0, null);
        insertState(conv2, U, 0, null);

        List<DmConversationEntity> page = conversationRepository.findPageAfter(U, T3, conv1, List.of(BlockDirectory.NOBODY), PageRequest.of(0, 10));

        assertThat(page).extracting(DmConversationEntity::getId).containsExactly(conv2);
    }

    @Test
    void findPeerIdsOfReturnsMessageCarryingPeersIncludingHiddenButNotMessageless() {
        UUID conv1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID conv3 = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
        UUID conv4 = UUID.fromString("00000000-0000-0000-0000-0000000000c4");
        insertConversation(conv1, U, P1, T3);
        insertConversation(conv3, U, P3, T1); // hidden but message-carrying → included
        insertConversation(conv4, U, P4, null); // message-less → excluded
        insertState(conv3, U, 0, T1);

        List<UUID> peers = conversationRepository.findPeerIdsOf(U);

        assertThat(peers).containsExactlyInAnyOrder(P1, P3);
    }

    // ---- native ON CONFLICT upserts ----------------------------------------

    @Test
    void conversationInsertIgnoringConflictIsIdempotentPerPair() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID second = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

        conversationRepository.insertIgnoringConflict(first, U, P1);
        conversationRepository.insertIgnoringConflict(second, U, P1); // same pair, must be a no-op

        DmConversationEntity conv = conversationRepository.findByUserAAndUserB(U, P1).orElseThrow();
        assertThat(conv.getId()).isEqualTo(first);
        Long rows = jdbc.queryForObject(
                "select count(*) from public.dm_conversations where user_a = ? and user_b = ?", Long.class, U, P1);
        assertThat(rows).isEqualTo(1L);
    }

    @Test
    void participantStateInsertIgnoringConflictCreatesBothRowsOnce() {
        UUID conv = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        insertConversation(conv, U, P1, null);

        stateRepository.insertIgnoringConflict(conv, U, P1);
        stateRepository.insertIgnoringConflict(conv, U, P1); // no-op the second time

        assertThat(stateRepository.findByConversationIdAndUserId(conv, U)).isPresent();
        assertThat(stateRepository.findByConversationIdAndUserId(conv, P1)).isPresent();
        Long rows = jdbc.queryForObject(
                "select count(*) from public.dm_participant_state where conversation_id = ?", Long.class, conv);
        assertThat(rows).isEqualTo(2L);
    }

    // ---- message history ----------------------------------------------------

    @Test
    void messageHistoryIsNewestFirstAndKeysetContinues() {
        UUID conv = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        insertConversation(conv, U, P1, T3);
        UUID m1 = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
        UUID m2 = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
        UUID m3 = UUID.fromString("00000000-0000-0000-0000-0000000000e3");
        insertMessage(m1, conv, U, T1);
        insertMessage(m2, conv, P1, T2);
        insertMessage(m3, conv, U, T3);

        List<DmMessageEntity> newestFirst =
                messageRepository.findByConversationIdOrderByCreatedAtDescIdDesc(conv, PageRequest.of(0, 10));
        assertThat(newestFirst).extracting(DmMessageEntity::getId).containsExactly(m3, m2, m1);

        List<DmMessageEntity> older = messageRepository.findPageAfter(conv, T3, m3, PageRequest.of(0, 10));
        assertThat(older).extracting(DmMessageEntity::getId).containsExactly(m2, m1);

        assertThat(messageRepository.findFirstByConversationIdOrderByCreatedAtDescIdDesc(conv))
                .get().extracting(DmMessageEntity::getId).isEqualTo(m3);
    }

    @Test
    void findByIdAndSenderIdScopesToTheSenderOnly() {
        UUID conv = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        insertConversation(conv, U, P1, T1);
        UUID m1 = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
        insertMessage(m1, conv, U, T1);

        assertThat(messageRepository.findByIdAndSenderId(m1, U)).isPresent();
        assertThat(messageRepository.findByIdAndSenderId(m1, P1)).isEmpty(); // not the sender → same 404
    }

    // ---- participant-state mutations ---------------------------------------

    @Test
    void registerIncomingMessageBumpsUnreadAndUnhides() {
        UUID conv = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        insertConversation(conv, U, P1, T1);
        insertState(conv, U, 0, T1); // starts hidden with zero unread

        stateRepository.registerIncomingMessage(conv, U);

        DmParticipantStateEntity st = stateRepository.findByConversationIdAndUserId(conv, U).orElseThrow();
        assertThat(st.getUnreadCount()).isEqualTo(1);
        assertThat(st.getHiddenAt()).isNull();
    }

    @Test
    void unhideClearsHiddenAtWithoutTouchingUnread() {
        UUID conv = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        insertConversation(conv, U, P1, T1);
        insertState(conv, U, 4, T1);

        stateRepository.unhide(conv, U);

        DmParticipantStateEntity st = stateRepository.findByConversationIdAndUserId(conv, U).orElseThrow();
        assertThat(st.getHiddenAt()).isNull();
        assertThat(st.getUnreadCount()).isEqualTo(4);
    }

    @Test
    void sumUnreadAggregatesAcrossConversationsAndCoalescesToZero() {
        assertThat(stateRepository.sumUnread(U)).isZero();

        UUID conv1 = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID conv2 = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        insertConversation(conv1, U, P1, T1);
        insertConversation(conv2, U, P2, T2);
        insertState(conv1, U, 2, null);
        insertState(conv2, U, 3, null);

        assertThat(stateRepository.sumUnread(U)).isEqualTo(5L);
    }
}
