package com.tweakdapp.backend.notification.internal.push;

import com.tweakdapp.backend.notification.internal.entities.UserDeviceEntity;
import com.tweakdapp.backend.notification.internal.repositories.UserDeviceRepository;
import com.tweakdapp.backend.testsupport.AbstractPostgresIT;
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
 * Real-SQL behaviours of {@link UserDeviceRepository} against the Supabase schema: the token-keyed
 * upsert and the device handoff it implements, the owner-scoped delete that stops one user
 * unregistering another's device, the CHECK constraints, and the cascade from {@code profiles}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserDeviceRepositoryIT extends AbstractPostgresIT {

    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    /** Real FCM tokens are ~160 chars; the column requires at least 32. */
    private static final String TOKEN = "d".repeat(64) + ":APA91bTESTTOKEN";
    private static final String OTHER_TOKEN = "e".repeat(64) + ":APA91bOTHERTOKEN";

    @Autowired
    private UserDeviceRepository userDeviceRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("delete from public.user_devices_firebase_token");
        createProfile(ALICE);
        createProfile(BOB);
    }

    private void createProfile(UUID id) {
        jdbc.update("insert into auth.users (id) values (?) on conflict do nothing", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?) on conflict do nothing",
                id, "u_" + id);
    }

    private UUID ownerOf(String token) {
        return jdbc.queryForObject(
                "select user_id from public.user_devices_firebase_token where token = ?", UUID.class, token);
    }

    private int rowCount() {
        return jdbc.queryForObject("select count(*) from public.user_devices_firebase_token", Integer.class);
    }

    // ---- upsert / handoff ---------------------------------------------------

    @Test
    void upsertInsertsANewDevice() {
        userDeviceRepository.upsert(UUID.randomUUID(), ALICE, TOKEN, "ios", "1.0.0+1", "en");

        assertThat(rowCount()).isEqualTo(1);
        assertThat(ownerOf(TOKEN)).isEqualTo(ALICE);
    }

    @Test
    void reRegisteringTheSameTokenIsIdempotentAndDoesNotDuplicate() {
        userDeviceRepository.upsert(UUID.randomUUID(), ALICE, TOKEN, "ios", "1.0.0+1", "en");
        userDeviceRepository.upsert(UUID.randomUUID(), ALICE, TOKEN, "ios", "1.0.1+2", "ro");

        assertThat(rowCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select app_version from public.user_devices_firebase_token where token = ?", String.class, TOKEN))
                .isEqualTo("1.0.1+2");
    }

    /**
     * The device-handoff case the contract calls for: a second account logs in on the same phone,
     * so the token must move to them and stop pushing to the previous owner.
     */
    @Test
    void reRegisteringAnExistingTokenReassignsItToTheNewUser() {
        userDeviceRepository.upsert(UUID.randomUUID(), ALICE, TOKEN, "ios", "1.0.0+1", "en");

        userDeviceRepository.upsert(UUID.randomUUID(), BOB, TOKEN, "ios", "1.0.0+1", "en");

        assertThat(rowCount()).isEqualTo(1);
        assertThat(ownerOf(TOKEN)).isEqualTo(BOB);
        assertThat(userDeviceRepository.findByUserIdIn(List.of(ALICE))).isEmpty();
    }

    // ---- owner-scoped delete ------------------------------------------------

    @Test
    void deleteByTokenAndUserIdRemovesTheCallersOwnDevice() {
        userDeviceRepository.upsert(UUID.randomUUID(), ALICE, TOKEN, "android", null, null);

        assertThat(userDeviceRepository.deleteByTokenAndUserId(TOKEN, ALICE)).isEqualTo(1);
        assertThat(rowCount()).isZero();
    }

    /** Security: one user must not be able to cut off another user's push by replaying a token. */
    @Test
    void deleteByTokenAndUserIdLeavesAnotherUsersDeviceUntouched() {
        userDeviceRepository.upsert(UUID.randomUUID(), ALICE, TOKEN, "android", null, null);

        assertThat(userDeviceRepository.deleteByTokenAndUserId(TOKEN, BOB)).isZero();
        assertThat(ownerOf(TOKEN)).isEqualTo(ALICE);
    }

    // ---- fan-out and pruning ------------------------------------------------

    @Test
    void findByUserIdInReturnsEveryDeviceOfEveryRequestedUser() {
        userDeviceRepository.upsert(UUID.randomUUID(), ALICE, TOKEN, "ios", null, null);
        userDeviceRepository.upsert(UUID.randomUUID(), ALICE, OTHER_TOKEN, "android", null, null);

        assertThat(userDeviceRepository.findByUserIdIn(List.of(ALICE, BOB)))
                .extracting(UserDeviceEntity::getToken)
                .containsExactlyInAnyOrder(TOKEN, OTHER_TOKEN);
    }

    @Test
    void deleteByTokenInPrunesDeadTokens() {
        userDeviceRepository.upsert(UUID.randomUUID(), ALICE, TOKEN, "ios", null, null);
        userDeviceRepository.upsert(UUID.randomUUID(), BOB, OTHER_TOKEN, "android", null, null);

        assertThat(userDeviceRepository.deleteByTokenIn(List.of(TOKEN, OTHER_TOKEN))).isEqualTo(2);
        assertThat(rowCount()).isZero();
    }

    // ---- constraints --------------------------------------------------------

    @Test
    void platformIsConstrainedToIosAndAndroid() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                userDeviceRepository.upsert(UUID.randomUUID(), ALICE, TOKEN, "windows", null, null));
    }

    @Test
    void tokenLengthIsBounded() {
        assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() ->
                userDeviceRepository.upsert(UUID.randomUUID(), ALICE, "tooshort", "ios", null, null));
    }

    @Test
    void deletingTheProfileCascadesToItsDevices() {
        userDeviceRepository.upsert(UUID.randomUUID(), ALICE, TOKEN, "ios", null, null);

        jdbc.update("delete from public.profiles where id = ?", ALICE);

        assertThat(rowCount()).isZero();
    }
}
