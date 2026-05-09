# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

Build / run / test use the Maven wrapper (Java 25 required):

- Run app: `./mvnw spring-boot:run`
- Build: `./mvnw clean package`
- All tests: `./mvnw test`
- Single test class: `./mvnw test -Dtest=ProfileControllerTest`
- Single test method: `./mvnw test -Dtest=ProfileControllerTest#methodName`
- Verify module boundaries only: `./mvnw test -Dtest=ModularityTests#verifiesModularStructure`

The app reads secrets from `.env` at the project root (loaded via `spring.config.import`). Required vars: `SUPABASE_DB_URL`, `SUPABASE_USER`, `SUPABASE_DB_PASSWORD`. Note that `.env` and `application.yaml` are gitignored in general — `application.yaml` is intentionally checked in here as the canonical non-secret config.

## Architecture

Spring Boot 4 + **Spring Modulith** project. Module boundaries are enforced by `ModularityTests` — adding cross-module references that violate the rules will fail that test, not the compiler.

### Module layout convention

Each top-level package under `com.carsocialmedia.backend` is a Spring Modulith `@ApplicationModule` declared in its `package-info.java`. The convention used by the `profile` module is the standard one to follow for new modules:

- **Module root package** (e.g. `profile/`) — the **public API** of the module: service interfaces (`ProfileService`), DTOs/records (`ProfileDto`). Other modules may depend on these.
- **`internal/` subpackage** (e.g. `profile/internal/`) — implementation details: `@RestController`, `@Service` impl, JPA `@Entity`, `JpaRepository`. These are package-private and **not visible to other modules** under Modulith rules. Do not make them `public` to work around access errors — that defeats the boundary.

The `shared` module is declared `type = ApplicationModule.Type.OPEN`, meaning any module may depend on anything inside it (used for cross-cutting concerns like security config). Keep it small.

### Security

`shared/security/SecurityConfig.java` configures stateless JWT auth via Supabase as an OAuth2 resource server. Key points:

- All requests require auth except `/public/**`; `/admin/**` requires `ROLE_ADMIN`.
- Roles are extracted from the JWT's `app_metadata.role` claim and prefixed with `ROLE_`. Missing claim → `ROLE_USER`.
- In controllers, get the Supabase user ID with `@AuthenticationPrincipal Jwt jwt` then `jwt.getSubject()` — that subject is the UUID primary key of `profiles.id`.
- `@EnableMethodSecurity` is on, so `@PreAuthorize` works on service/controller methods.

### Persistence

- Postgres (Supabase-hosted), JPA/Hibernate.
- `hibernate.ddl-auto: validate` — **the schema is owned by Supabase, not the app**. Do not add `@GeneratedValue` strategies that assume Hibernate creates tables, and do not change entity columns without a corresponding Supabase migration. JPA will fail at startup if the entity and DB diverge.
- `open-in-view: false` — lazy associations must be resolved inside `@Transactional` boundaries (typically the service layer), not in controllers/serializers.

### REST conventions

- Base path: `/api/v1/<module>/...` (see `ProfileController`).
- Controllers stay thin: pull `userId` from the JWT, delegate to a service interface from the module's public API, return DTO records.

## Lombok

Lombok is enabled via annotation processor (configured in `pom.xml`). Entities use `@Getter`/`@Setter`. Build excludes Lombok from the runtime jar.