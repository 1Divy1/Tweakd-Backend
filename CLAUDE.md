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

See [`CONTEXT.md`](CONTEXT.md) for the full architecture overview and module READMEs for module-specific details.

### Rules to enforce

- **Module boundaries** — do not make types in `internal/` subpackages `public` to work around a cross-module access error. That defeats the Modulith boundary. Fix the design instead.
- **Schema ownership** — the DB schema is owned by Supabase, not Hibernate (`ddl-auto: validate`). Do not add `@GeneratedValue` strategies that assume Hibernate creates tables. Do not change entity columns without a corresponding Supabase migration — a divergence crashes startup.
- **Lazy loading** — `open-in-view: false`. Resolve lazy associations inside `@Transactional` boundaries (service layer), never in controllers or serializers.
- **JWT user ID** — in controllers, get the authenticated user's Supabase UUID via `@AuthenticationPrincipal Jwt jwt` → `jwt.getSubject()`. That subject is the UUID primary key of `profiles.id`.
- **Controllers stay thin** — extract `userId` from the JWT, delegate to the module's service interface, return DTO records. No business logic in controllers.
- **REST base path** — `/api/v1/<module>/...`

## Lombok

Lombok is enabled via annotation processor (configured in `pom.xml`). Entities use `@Getter`/`@Setter`. Build excludes Lombok from the runtime jar.