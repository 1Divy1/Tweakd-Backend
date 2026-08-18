package com.carsocialmedia.backend.profile.internal.repository;

import com.carsocialmedia.backend.profile.internal.entity.ProfileEntity;
import com.carsocialmedia.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Real-SQL behaviors of the profile repositories against the Supabase schema in a PostGIS container:
 * the case-insensitive username prefix search (ordering), the bulk-by-id and business-only custom
 * queries, the ban-state projection, and the username UNIQUE constraint the onboarding fallback
 * relies on.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProfileRepositoryIT extends AbstractPostgresIT {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    // ---- fixture helpers ----------------------------------------------------

    private void createProfile(UUID id, String username) {
        jdbc.update("insert into auth.users (id) values (?)", id);
        jdbc.update("insert into public.profiles (id, username) values (?, ?)", id, username);
    }

    private void setBusiness(UUID id) {
        jdbc.update("update public.profiles set is_business = true where id = ?", id);
    }

    // ---- username lookups + prefix search -----------------------------------

    @Test
    void findByUsernameReturnsTheMatchingProfile() {
        createProfile(A, "racer");

        assertThat(profileRepository.findByUsername("racer"))
                .get().extracting(ProfileEntity::getId).isEqualTo(A);
        assertThat(profileRepository.findByUsername("nobody")).isEmpty();
    }

    @Test
    void prefixSearchIsCaseInsensitiveAndOrderedAscending() {
        createProfile(A, "carla");
        createProfile(B, "Carlos");   // capitalized — must still match "car"
        createProfile(C, "bianca");   // no match

        List<ProfileEntity> matches =
                profileRepository.findTop20ByUsernameStartingWithIgnoreCaseOrderByUsernameAsc("car");

        assertThat(matches).extracting(ProfileEntity::getUsername)
                .containsExactly("carla", "Carlos"); // DB collation orders case-insensitively
    }

    @Test
    void findAllByIdInHydratesOnlyTheRequestedProfiles() {
        createProfile(A, "a_user");
        createProfile(B, "b_user");
        createProfile(C, "c_user");

        assertThat(profileRepository.findAllByIdIn(List.of(A, C)))
                .extracting(ProfileEntity::getId)
                .containsExactlyInAnyOrder(A, C);
    }

    // ---- custom @Query surfaces ---------------------------------------------

    @Test
    void findBusinessIdsReturnsOnlyBusinessAccountsAmongTheGivenIds() {
        createProfile(A, "shop");
        createProfile(B, "person");
        createProfile(C, "dealer");
        setBusiness(A);
        setBusiness(C);

        assertThat(profileRepository.findBusinessIds(List.of(A, B, C)))
                .containsExactlyInAnyOrder(A, C);
    }

    @Test
    void findBanStateProjectsTheBanColumns() {
        createProfile(A, "banned_user");
        Instant until = Instant.parse("2026-09-01T00:00:00Z");
        jdbc.update("update public.profiles set is_banned = true, banned_until = ? where id = ?",
                OffsetDateTime.ofInstant(until, ZoneOffset.UTC), A);

        var state = profileRepository.findBanState(A).orElseThrow();

        assertThat(state.getBanned()).isTrue();
        assertThat(state.getBannedUntil()).isEqualTo(until);
    }

    @Test
    void findBanStateOfAnUnbannedUserReadsAsNotBanned() {
        createProfile(A, "clean_user");

        var state = profileRepository.findBanState(A).orElseThrow();

        assertThat(state.getBanned()).isFalse();
        assertThat(state.getBannedUntil()).isNull();
    }

    // ---- username UNIQUE constraint (onboarding fallback relies on it) -------

    @Test
    void usernameUniqueConstraintRejectsADuplicate() {
        createProfile(A, "unique_name");
        jdbc.update("insert into auth.users (id) values (?)", B);

        assertThatExceptionOfType(DuplicateKeyException.class).isThrownBy(() ->
                jdbc.update("insert into public.profiles (id, username) values (?, ?)", B, "unique_name"));
    }
}
