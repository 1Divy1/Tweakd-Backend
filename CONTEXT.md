# Codebase Context

## Stack

| Layer | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4 + Spring Modulith |
| Database | PostgreSQL hosted on Supabase |
| ORM | JPA / Hibernate |
| Build | Maven (wrapper: `./mvnw`) |
| Boilerplate | Lombok |

## Commands

```bash
./mvnw spring-boot:run          # run the app
./mvnw clean package            # build jar
./mvnw test                     # all tests
./mvnw test -Dtest=ClassName    # single test class
./mvnw test -Dtest=ClassName#method  # single test method
./mvnw test -Dtest=ModularityTests#verifiesModularStructure  # module boundary check only
```

Secrets are loaded from `.env` at the project root via `spring.config.import`. Required: `SUPABASE_DB_URL`, `SUPABASE_USER`, `SUPABASE_DB_PASSWORD`. Both `.env` and `application.yaml` are gitignored — `application.yaml` is intentionally committed as the canonical non-secret config.

## Architecture

The project uses **Spring Modulith** to enforce hard module boundaries at test time. Each top-level package under `com.carsocialmedia.backend` is an `@ApplicationModule` declared in its `package-info.java`.

### Module layout

```
com.carsocialmedia.backend/
├── profile/          ← public API: service interface, DTOs, domain events, exceptions
│   └── internal/     ← private: controller, service impl, entity, repository
├── follow/           ← public API: service interface, DTOs, enums, exceptions
│   └── internal/     ← private: controller, service impl, entity, repository, event listener
├── garage/           ← public API: service interface, DTOs, exceptions
│   └── internal/     ← private: controllers, service impl, entities (garage / car / mod / images / reference), repositories
├── storage/          ← public API: StorageService (presigned URL generation)
│   └── internal/     ← private: StorageServiceImpl, SupabaseStorageClient, StorageProperties
├── dms/              ← public API: DmsService, DTOs (incl. the WebSocket event envelope), exceptions
│   └── internal/     ← private: REST controller, typing STOMP controller, service impl, event pushers, entities, repositories
├── presence/         ← public API: PresenceService, PresenceDto, UserPresenceChangedEvent
│   └── internal/     ← private: in-memory session registry, WS lifecycle listeners, grace sweep + flush (@Scheduled), entity, repo, controller
├── tags/             ← public API: TagsService, DTOs (merged tags feed). Owns no data — composes posts + forums + garage
│   └── internal/     ← private: controller, service impl (four-stream merge), cursor
├── business/         ← public API: BusinessService, DTOs (map pin / profile / hours / type). Read-only
│   └── internal/     ← private: controller, service impl, entities, repositories, PostGIS radius search, open-now derivation
├── mapevents/        ← public API: MapEventsService, DTOs, domain events (approval / car / organizer)
│   └── internal/     ← private: controller, service impl, entities, repositories, PostGIS radius search, keyset cursor
└── shared/           ← OPEN module: security config, realtime (STOMP WebSocket at /ws), exception hierarchy, global handler, moderation + staff SPIs
    └── geo/          ← app-wide geo reference data: cities + countries entities/repos/DTOs, GeoSupport (lat/lng ↔ JTS Point)
```

**Public root package** — what other modules may import: service interfaces, DTOs/records, domain events, exception types.

**`internal/` subpackage** — implementation details. Modulith treats these as package-private to the module. Never make internal types `public` just to work around a cross-module access error; that defeats the boundary.

**`shared/`** is declared `type = ApplicationModule.Type.OPEN`, so any module can import anything from it. Keep it limited to genuine cross-cutting concerns.

Module boundaries are verified by `ModularityTests`. A violation fails that test, not the compiler.

## Security

`shared/security/SecurityConfig.java` configures stateless JWT authentication using Supabase as an OAuth2 resource server.

- All endpoints require authentication unless under `/public/**`. The WebSocket handshake at
  `/ws/**` is also `permitAll` — the real authentication happens at the STOMP CONNECT frame
  (`shared/realtime/JwtChannelInterceptor`, same JWT validation as REST).
- `/api/v1/admin/**` requires `ROLE_ADMIN` (Supabase `app_metadata.role = 'admin'`); the `admin`
  module then applies fine-grained team-role capability checks (`admin_team_members`). Approving
  user-submitted map events is gated on `APPROVE_EVENTS`, held by `owner` and `senior_admin` only.
- **Staff accounts are separate from app accounts**: dashboard staff are profile-less Supabase auth
  users (the `handle_new_user` trigger skips them), invited by email through the admin module's
  `SupabaseAuthAdminClient` (service-role key). Their identity lives on `admin_team_members`
  (email / display name / avatar) and is resolved via `shared/staff/StaffDirectory`, never via
  `profiles`. A person who is both an app user and staff has two logins.
- Banned users (`profiles.is_banned`) are rejected with 403 on every request by the profile
  module's `BannedUserInterceptor` (60s cache; a missing profile — i.e. staff — reads as not banned).
- Roles come from the JWT claim `app_metadata.role`, prefixed with `ROLE_`. Missing claim defaults to `ROLE_USER`.
- In controllers, retrieve the authenticated user's Supabase UUID via `@AuthenticationPrincipal Jwt jwt` → `jwt.getSubject()`. That subject is the primary key of the `profiles` table (for app users; staff have no profile row).
- `@EnableMethodSecurity` is active — `@PreAuthorize` works on service and controller methods.

## Persistence

- `hibernate.ddl-auto: validate` — **the schema is owned by Supabase, not Hibernate**. Never add `@GeneratedValue` strategies that assume Hibernate creates tables. Entity columns must match the DB schema exactly; a divergence crashes startup.
- `open-in-view: false` — lazy associations must be resolved inside `@Transactional` boundaries (usually the service layer).
- Supabase database triggers handle certain side effects (counter increments, default field values). See each module's README for the triggers that affect it.

## REST conventions

- Base path: `/api/v1/<module>/...`
- Controllers stay thin: extract `userId` from the JWT, delegate to the module's service interface, return DTO records.
- Error responses follow the `ErrorResponse` shape defined in `shared/exception/`.

## Lombok

Entities use `@Getter` / `@Setter`. The Lombok annotation processor is wired in `pom.xml`; Lombok is excluded from the runtime jar.

## Module READMEs

Each module has a `README.md` with its specific API surface, endpoints, entities, exceptions, and Supabase trigger dependencies:

- [`profile` module](src/main/java/com/carsocialmedia/backend/profile/README.md)
- [`follow` module](src/main/java/com/carsocialmedia/backend/relationships/README.md)
- [`garage` module](src/main/java/com/carsocialmedia/backend/garage/README.md)
- [`posts` module](src/main/java/com/carsocialmedia/backend/posts/README.md)
- [`feed` module](src/main/java/com/carsocialmedia/backend/feed/README.md)
- [`tags` module](src/main/java/com/carsocialmedia/backend/tags/README.md)
- [`business` module](src/main/java/com/carsocialmedia/backend/business/README.md)
- [`mapevents` module](src/main/java/com/carsocialmedia/backend/mapevents/README.md)
- [`forums` module](src/main/java/com/carsocialmedia/backend/forums/README.md)
- [`report` module](src/main/java/com/carsocialmedia/backend/report/README.md)
- [`feedback` module](src/main/java/com/carsocialmedia/backend/feedback/README.md)
- [`notification` module](src/main/java/com/carsocialmedia/backend/notification/README.md)
- [`support` module](src/main/java/com/carsocialmedia/backend/support/README.md)
- [`dms` module](src/main/java/com/carsocialmedia/backend/dms/README.md)
- [`presence` module](src/main/java/com/carsocialmedia/backend/presence/README.md)
- [`admin` module](src/main/java/com/carsocialmedia/backend/admin/README.md)
- [`storage` module](src/main/java/com/carsocialmedia/backend/storage/README.md)
- [`shared` module](src/main/java/com/carsocialmedia/backend/shared/README.md)

The admin-dashboard build-out (modules `notification` / `support` / `admin`, feedback board,
moderation, bans) is documented end-to-end in [`ADMIN_DASHBOARD_PROGRESS.md`](ADMIN_DASHBOARD_PROGRESS.md).
