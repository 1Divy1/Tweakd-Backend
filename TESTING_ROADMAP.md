# Testing Roadmap

The plan and conventions for building the automated test suite. This file is mostly **static**
(decisions, conventions, phase definitions). Live per-task progress is tracked in
[`TESTING_PROGRESS.md`](TESTING_PROGRESS.md) — update that file as work happens, not this one.

**How to resume in a new session:** read this file, then `TESTING_PROGRESS.md`, then the
README of the module the current phase targets. That's all the context needed.

---

## Decisions (made 2026-07-16, confirmed by owner)

1. **DB strategy: Testcontainers + schema dump.** Tests that touch the database run against a
   throwaway PostgreSQL container (image: `postgis/postgis`, because of hibernate-spatial)
   loaded with a schema dump from the live Supabase project — tables, functions, **and
   triggers**, so trigger side effects (counters, defaults, `brand_id` etc.) get real coverage.
   The dump lives in the repo and is refreshed via a script whenever the Supabase schema changes.
2. **Priority order:** dms + presence first (newest, least battle-tested), then core
   (profile / follow / garage), then content (posts / feed / forums), then the admin cluster,
   then storage.
3. **No tests against the live Supabase DB.** The suite must pass offline.

## Test layers & where each kind of logic gets tested

| Layer | Tooling | What it covers | Speed |
|---|---|---|---|
| Unit | JUnit 5 + Mockito, no Spring | Service impl business rules, edge cases, exceptions | ms |
| Web slice | `@WebMvcTest` + `spring-security-test` | Routes, status codes, JSON shapes, validation, authz rules, JWT subject extraction | ~100ms |
| Repository / integration | `@DataJpaTest` (or full context) + Testcontainers | Custom queries, keyset pagination, ON CONFLICT upserts, DB triggers, entity↔schema fit | seconds |
| Modulith | `ApplicationModules` verify + `@ApplicationModuleTest` + `Scenario` | Module boundaries (exists: `ModularityTests`), published domain events | seconds |

Rule of thumb per module: **most tests are unit tests**; one web-slice class per controller;
repository tests only for hand-written queries and trigger-dependent flows (not for derived
`findByX` methods Spring generates — those are Spring's job to get right).

## Conventions

- Test classes mirror the main package: `src/test/java/com/tweakdapp/backend/<module>/...`.
  Tests for `internal/` classes live in the same package so package-private access works.
- Naming: `<Class>Test` (unit), `<Controller>WebTest` (web slice), `<Repository>IT`
  (Testcontainers-backed). Method names describe behavior: `deletingThreadWithRepliesAnonymizesIt()`.
- Structure every test as arrange / act / assert. One behavior per test method.
- Shared fixtures live in `src/test/java/.../testsupport/` (JWT builders, entity/DTO factories,
  the base Testcontainers class). Keep factories dumb — explicit values, no randomness.
- Web tests build fake JWTs with `SecurityMockMvcRequestPostProcessors.jwt()` — never real
  Supabase tokens. Subject = a fixed test UUID. Use the `testsupport/TestJwts` helpers.
- Controller slice tests use `@AppWebMvcTest(TheController.class)` (in `testsupport/`), never
  raw `@WebMvcTest`: it imports the real `SecurityConfig` (route rules + role mapping apply) and
  `BanEnforcementTestStub` (satisfies the globally registered banned-user interceptor, which the
  web slice always picks up).
- Assertions: AssertJ (`assertThat(...)`) — comes with the Boot test starters.
- Never weaken module boundaries (make internals public) to make something testable — that's a
  design smell; test through the module's public API or move the test into the package.

## Infrastructure design (built in Phase 0)

- **Dependencies to add (`pom.xml`, test scope):** `spring-boot-testcontainers`,
  `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`. Verify Mockito/AssertJ
  arrive via the existing Boot 4 test starters; add explicitly if not.
- **Schema dump:** `scripts/dump-schema.sh` → `src/test/resources/db/schema.sql`. Uses `pg_dump`
  with credentials from `.env`, `--schema-only --no-owner --no-privileges`, `public` schema.
  Known gotchas to solve here:
  - `profiles.id` (and staff logic) references `auth.users` — the dump must be post-processed
    to create a **minimal `auth.users` stub table** (or strip the FK) since we don't dump
    Supabase's `auth` schema.
  - Extensions: the dump script must emit `CREATE EXTENSION IF NOT EXISTS postgis` (and any
    others in use, e.g. `pg_trgm`) before the tables.
  - `handle_new_user` trigger lives on `auth.users` — decide in Phase 0 whether to recreate it
    on the stub or have test fixtures insert `profiles` rows directly (simpler; recommended).
- **Base class:** `testsupport/AbstractPostgresIT` — singleton `PostgreSQLContainer`
  (`postgis/postgis:16-3.4` or matching Supabase's PG major), `@ServiceConnection`, schema.sql
  applied once per JVM. All `*IT` classes extend it.
- **Test config:** `src/test/resources/application.yaml` that does NOT import `.env` and keeps
  `ddl-auto: validate` (validation against the dump is itself a valuable test — it proves the
  entities match the real schema).
- **`BackendApplicationTests`** currently context-loads against whatever `.env` points at;
  repoint it at the Testcontainer so `./mvnw test` never needs the live DB.
- **CI note (future):** everything here runs anywhere Docker runs; wire into cloudbuild later.

## Phases

Each module phase follows the same recipe:
1. Read the module README; inventory endpoints, service methods, custom queries, triggers.
2. Write the inventory as a checklist into `TESTING_PROGRESS.md` (this is the session-resume point).
3. Unit tests for the service impl (bulk of the work).
4. Web-slice tests per controller (happy path + auth + validation + error mapping).
5. Repository ITs for hand-written queries / trigger-dependent flows only.
6. `@ApplicationModuleTest` if the module publishes or consumes domain events.
7. Full `./mvnw test` green; tick the phase in `TESTING_PROGRESS.md`.

| Phase | Scope | Notes |
|---|---|---|
| 0 | Foundation | Deps, schema dump script + dump, base IT class, test config, JWT fixtures, repoint context-load test |
| 1 | shared | SecurityConfig route rules, role mapping (missing claim → ROLE_USER), JwtChannelInterceptor, BannedUserInterceptor (+60s cache), global exception handler → ErrorResponse shape |
| 2 | dms | Read receipts, soft delete, self-DM rules, conversation queries, STOMP event envelope/pushers |
| 3 | presence | Session registry, 20s grace + sweep/flush scheduling logic, peer fan-out, UserPresenceChangedEvent |
| 4 | profile | Incl. BannedUserInterceptor integration angle if not fully covered in Phase 1 |
| 5 | follow (relationships) | Follow/unfollow rules, events consumed/published |
| 6 | garage | Reference-data DTO flattening, NOT NULL fuel/status invariants |
| 7 | posts | CRUD, tagging, images, grids |
| 8 | feed | ranking_score keyset pagination — repository IT territory |
| 9 | forums | Anonymize-vs-hard delete, per-level reply pagination, ON CONFLICT likes, brand_id trigger |
| 10 | report | GET /reports/mine + filing endpoints living in posts/profile |
| 11 | feedback, notification, support | Smaller modules, batch them |
| 12 | admin | Capability matrix, staff directory, bans — security-sensitive, be thorough |
| 13 | storage | Presigned URL generation — unit tests with mocked S3 client |

Coverage is a byproduct, not a goal: no percentage targets. A phase is "done" when the
inventory checklist in `TESTING_PROGRESS.md` is fully ticked and the whole suite is green.
