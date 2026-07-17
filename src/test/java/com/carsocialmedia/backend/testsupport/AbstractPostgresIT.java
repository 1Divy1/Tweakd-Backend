package com.carsocialmedia.backend.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * Base class for tests that need a real PostgreSQL database ({@code *IT} classes).
 *
 * <p>Starts a single PostGIS container per JVM (singleton pattern — started once in the
 * static initializer, shared by every IT class, reaped by Testcontainers when the JVM
 * exits) and loads the Supabase schema dump ({@code db/schema.sql}, regenerated with
 * {@code ./scripts/dump-schema.sh}) via the container's init directory.
 *
 * <p>Because {@code ddl-auto: validate} stays on, any context that boots against this
 * container proves the JPA entities match the real Supabase schema.
 */
public abstract class AbstractPostgresIT {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"))
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql")
            // Loaded after the schema: seeds lookup rows that NOT NULL / RESTRICT FKs need
            // (the schema-only dump omits Supabase's seeded reference data).
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("db/seed.sql"),
                    "/docker-entrypoint-initdb.d/02-seed.sql");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }
}
