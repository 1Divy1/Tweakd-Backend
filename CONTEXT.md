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
└── shared/           ← OPEN module: security config, exception hierarchy, global handler
```

**Public root package** — what other modules may import: service interfaces, DTOs/records, domain events, exception types.

**`internal/` subpackage** — implementation details. Modulith treats these as package-private to the module. Never make internal types `public` just to work around a cross-module access error; that defeats the boundary.

**`shared/`** is declared `type = ApplicationModule.Type.OPEN`, so any module can import anything from it. Keep it limited to genuine cross-cutting concerns.

Module boundaries are verified by `ModularityTests`. A violation fails that test, not the compiler.

## Security

`shared/security/SecurityConfig.java` configures stateless JWT authentication using Supabase as an OAuth2 resource server.

- All endpoints require authentication unless under `/public/**`.
- `/admin/**` requires `ROLE_ADMIN`.
- Roles come from the JWT claim `app_metadata.role`, prefixed with `ROLE_`. Missing claim defaults to `ROLE_USER`.
- In controllers, retrieve the authenticated user's Supabase UUID via `@AuthenticationPrincipal Jwt jwt` → `jwt.getSubject()`. That subject is the primary key of the `profiles` table.
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
- [`follow` module](src/main/java/com/carsocialmedia/backend/follow/README.md)
- [`shared` module](src/main/java/com/carsocialmedia/backend/shared/README.md)
