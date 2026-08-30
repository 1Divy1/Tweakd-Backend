package com.tweakdapp.backend;

import com.tweakdapp.backend.testsupport.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the full application context against the Testcontainers database.
 *
 * <p>This is a real test despite the empty body: with {@code ddl-auto: validate}, a
 * successful boot proves every JPA entity matches the Supabase schema dump — the
 * "divergence crashes startup" failure mode gets caught here instead of at deploy time.
 */
@SpringBootTest
class ApplicationSmokeIT extends AbstractPostgresIT {

    @Test
    void contextLoadsAndEntitiesMatchSchema() {
    }
}
