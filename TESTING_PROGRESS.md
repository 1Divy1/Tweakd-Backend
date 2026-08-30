# Testing Progress

Live tracker for the plan in [`TESTING_ROADMAP.md`](TESTING_ROADMAP.md). Update this file as
work happens. When starting a module phase, first expand its section with the full inventory
checklist (step 1–2 of the phase recipe), then work through it.

## Status

| Phase | Scope | Status |
|---|---|---|
| 0 | Foundation | DONE (2026-07-16) |
| 1 | shared | DONE (2026-07-16) |
| 2 | dms | DONE (2026-07-16) |
| 3 | presence | DONE (2026-07-16) |
| 4 | profile | DONE (2026-07-16) |
| 5 | follow (`relationships` module) | DONE (2026-07-16) |
| 6 | garage | NOT STARTED |
| 7 | posts | NOT STARTED |
| 8 | feed | NOT STARTED |
| 9 | forums | NOT STARTED |
| 10 | report | NOT STARTED |
| 11 | feedback / notification / support | NOT STARTED |
| 12 | admin | NOT STARTED |
| 13 | storage | NOT STARTED |

Statuses: NOT STARTED → IN PROGRESS → DONE. Add a one-line note next to IN PROGRESS phases
saying exactly what's next (the session-resume pointer).

## Phase 0 — Foundation — DONE 2026-07-16

- [x] Add test deps to `pom.xml` — note: Testcontainers 2.x artifacts are `testcontainers-postgresql` / `testcontainers-junit-jupiter` (renamed from 1.x)
- [x] Confirm Mockito + AssertJ on the test classpath (Mockito 5.20, AssertJ 3.27, JUnit Jupiter 6 — all via Boot 4 starters)
- [x] `scripts/dump-schema.sh` — dockerized `pg_dump` (postgres:17-alpine; local pg_dump is v14, too old), creds parsed from `.env`
- [x] Dump generated: `src/test/resources/db/schema.sql` (69 tables, 50 triggers; loads cleanly)
- [x] `testsupport/AbstractPostgresIT` — singleton `postgis/postgis:17-3.5` container, schema via `/docker-entrypoint-initdb.d`, `@DynamicPropertySource` (not `@ServiceConnection`; singleton pattern is simpler across many IT classes)
- [x] `src/test/resources/application.yaml` — shadows the main one entirely; dummy supabase/cloudflare values, no `.env` import, `ddl-auto: validate` kept
- [x] `ApplicationSmokeIT` replaces `BackendApplicationTests` — full context boots against the container; entity↔schema validation passes
- [x] `testsupport/TestJwts` — `user()` / `admin()` MockMvc post-processors with Supabase-shaped claims
- [x] surefire config in `pom.xml`: `*IT` classes included in `./mvnw test` (default includes would skip them)
- [ ] Fixture factories — MOVED into each module phase (build them when the phase's entities are in front of us)
- [x] `./mvnw test` green (3 tests, ~20s)

## Phase 1 — shared — DONE 2026-07-16

36 tests total in the suite after this phase, all green (`./mvnw test`, ~35s).

- [x] `shared/security/JwtAuthenticationConverterTest` (5) — app_metadata.role → ROLE_X mapping, ROLE_USER fallback, uppercasing, principal name = subject. Gotcha: Spring Security 7 attaches an extra `FACTOR_BEARER` authority to every JWT auth — assert only on `ROLE_*` authorities.
- [x] `shared/security/SecurityRulesWebTest` (5) — 401 unauthenticated, 200 + subject via `@AuthenticationPrincipal`, 403 user on `/api/v1/admin/**`, 200 admin, `/ws/**` open. Uses a nested `ProbeController`; needs BOTH `@AppWebMvcTest(ProbeController.class)` (excludes real controllers) AND `@Import(ProbeController.class)` (nested test classes are excluded from slice scanning, so the controllers attribute alone 404s).
- [x] `shared/realtime/JwtChannelInterceptorTest` (8) — STOMP CONNECT token validation, SUBSCRIBE own-queue enforcement, passthrough frames.
- [x] `shared/exception/GlobalExceptionHandlerTest` (3) — ApiException → status/body mapping, validation-error first-message-wins.
- [x] `profile/internal/BanCacheTest` (7) — permanent/temp/expired bans, 60s TTL caching, evict.
- [x] `profile/internal/BannedUserInterceptorTest` (5) — passthroughs (unauthenticated / non-JWT / non-UUID) + banned → 403 JSON.
- [x] `testsupport/AppWebMvcTest` + `profile/internal/BanEnforcementTestStub` — the composed annotation ALL controller slice tests must use (see roadmap conventions).

## Phase 2 — dms — DONE 2026-07-16

96 tests total in the suite after this phase, all green (`./mvnw test`, ~40s). 60 new dms tests
(25 + 15 + 2 + 10 + 3 + 5) across 6 classes; no main-source changes.

- [x] `dms/internal/DmsServiceImplTest` (25) — pure unit, mocked repos/profile/presence/publisher/pusher. Send: self-DM + unknown-recipient guards fire before persistence, content strip + 140-char preview truncation (full content still stored), first-message create via both ON CONFLICT upserts, pair canonicalization regardless of sender order, recipient unread bump + sender unhide + `message.created` publish. markRead: 404 for non-participant, watermark advance + zero-unread + `conversation.read` publish, **no event when already at latest**, empty-conversation keeps null watermark. deleteMessage: 404 when not your message, idempotent no-op on already-deleted (no event, no conversation fetch), latest-message delete blanks chats-list preview, older-message delete leaves preview intact. hide: 404 non-participant, sets hiddenAt + zeroes unread. countUnread delegate. relayTyping: relays to peer for participant, silently drops for stranger / missing conversation. listConversations: peer/unread/presence mapping + null cursor on last page, size+1 look-ahead trim + cursor emission, page-size clamp (1000 → 51). listMessages: 404 non-participant, newest-first page + peer read watermark.
- [x] `dms/internal/DmEventPusherTest` (5) — mocked `SimpMessagingTemplate`; verifies destination `/queue/dms`, recipient fan-out, and discriminated `DmSocketEvent` payload for message.created (recipient + sender), message.deleted (both participants), conversation.read (peer + reader), typing (peer only), presence (every shared peer).
- [x] `dms/internal/DmsControllerWebTest` (15) — `@AppWebMvcTest(DmsController.class)` + `@MockitoBean DmsService`. 401 unauthenticated; JWT-subject + default page-size delegation; snake_case DTO shapes (`next_cursor`, `peer_last_read_message_id`, `conversation_id`, `sender_id`); 201 on send with body echo + captured request; bean-validation 400s (blank / missing recipient / >2000 chars) short-circuit the service; exception→status mapping (self-DM 400, unknown recipient 404, not-your-conversation 404, not-your-message 404); read 200 watermark; hide/delete 204 delegation; unread-count badge.
- [x] `dms/internal/DmsTypingControllerTest` (3) — relays with principal-name as typist id; drops pings with no principal / no conversation id.
- [x] `dms/internal/DmPresencePusherTest` (2) — fans a presence flip to all DM peers; no sends when the user has no message-carrying conversation.
- [x] `dms/internal/repositories/DmsRepositoryIT` (10) — `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` + `AbstractPostgresIT`, JdbcTemplate fixtures (auth.users + profiles + dm rows). Chats-list keyset: excludes hidden + message-less, newest-first ordering; `findPageAfter` strict continuation; `findPeerIdsOf` includes hidden but not message-less. Native ON CONFLICT: conversation + participant-state upserts idempotent per pair. Message history: newest-first + keyset continuation + latest fetch; `findByIdAndSenderId` sender-scoped (wrong sender → empty). Participant-state: `registerIncomingMessage` bumps unread + unhides, `unhide` clears hiddenAt only, `sumUnread` aggregates + coalesces to 0.

**Untestable without main-code changes:** none — the whole module was reachable via package-private
test classes (all six classes live in `dms.internal` / `dms.internal.repositories`). The STOMP
CONNECT/SUBSCRIBE auth (`JwtChannelInterceptor`) was already covered in Phase 1; end-to-end
two-user STOMP delivery over a live socket is out of scope for the slice/unit level (same "untested
vs live users" caveat the module itself carries) and would need a full-broker integration harness.

**No dms bugs found.** Note (not a bug): `DmsServiceImpl.listConversations` dereferences
`presence.get(peer).online()` without a null guard — safe only because the `PresenceService`
contract guarantees an entry for every requested id; a future contract change there would NPE.

## Phase 3 — presence — DONE 2026-07-16

142 tests total in the suite after this phase, all green (`./mvnw test`, ~45s). 46 new presence
tests (16 + 7 + 6 + 6 + 6 + 5) across 6 classes; no main-source changes.

- [x] `presence/internal/PresenceRegistryTest` (16) — pure unit, the in-memory state machine. First
      session → online transition (`addSession` true); second concurrent session / duplicate session
      id → no re-transition (false). Grace window: removing the last session queues pending offline,
      a reconnect inside the window cancels it with no transition; removing one of several sessions
      keeps the user online and never enters grace. Sweep: `sweepExpired` finalizes only expired
      pending users and returns each once, leaving live users alone; a double-disconnect of the same
      session does not double-enter grace; removing an unknown session is a no-op. `isOnline` true
      while sessioned OR in grace, false for unknown; `onlineUsers` excludes grace-window users and
      is a defensive copy. Grace timing driven deterministically by the `Duration` arg (negative →
      future cutoff → forced expiry; large positive → past cutoff → nothing expires) — no clock.
- [x] `presence/internal/PresenceLifecycleTest` (7) — pure unit, mocked registry/repo/publisher.
      `OFFLINE_GRACE == 20s`. markOnline upserts + publishes `online=true`/null-last-seen. sweepOffline
      calls `sweepExpired(OFFLINE_GRACE)`, upserts + publishes `online=false` per expired user, the
      offline event's last-seen equals the persisted instant, and nothing-expired touches neither DB
      nor event bus. flush batch-`touchAll`s every online user and skips the DB when nobody is online.
- [x] `presence/internal/PresenceSessionListenerTest` (6) — pure unit, mocked registry+lifecycle,
      real `SessionConnected/DisconnectEvent` built from STOMP frames (`StompHeaderAccessor` +
      `setLeaveMutable` + `MessageBuilder`, principal name = user UUID). First-session connect →
      `markOnline`; non-transitioning connect → no `markOnline`; connect with no principal / no
      session id → ignored. Disconnect only `removeSession`s (offline deferred to sweep); disconnect
      with no principal → ignored.
- [x] `presence/internal/PresenceServiceImplTest` (6) — pure unit, mocked registry+repo. Online →
      `online=true`/null last-seen, no DB hit; offline with watermark → last-seen, offline never-seen
      → null; only offline ids queried; every requested id gets exactly one entry; duplicate ids
      collapse to a single entry + single query.
- [x] `presence/internal/PresenceControllerWebTest` (6) — `@AppWebMvcTest(PresenceController.class)`
      + `@MockitoBean PresenceService`. 401 unauthenticated; snake_case array (`user_id`, `online`,
      `last_seen_at`) for online + offline; comma-separated `user_ids` parsed and delegated
      (captured `containsExactly`); >100 ids → 400 without reaching the service; missing param → 400.
- [x] `presence/internal/UserPresenceRepositoryIT` (5) — `@DataJpaTest` +
      `@AutoConfigureTestDatabase(replace = NONE)` + `AbstractPostgresIT`, JdbcTemplate fixtures
      (auth.users + profiles). Native FK-safe upsert inserts a new row, on-conflict updates in place
      (row count stays 1), and is a silent no-op for a user with no profile row (no FK violation);
      `touchAll` bulk-updates watermarks; `findAllById` reads them back.

**Untestable without main-code changes:** none — every presence class is reachable via
package-private test classes in `presence.internal`. The dms-side fan-out (`DmPresencePusher`) and
the STOMP CONNECT/SUBSCRIBE auth were already covered in Phases 1–2 and are not re-tested here; the
lifecycle's `Instant.now()` calls are exercised via mock argument-matchers rather than a fixed clock
(the class takes no injectable time source — see gotcha).

**No presence bugs found.**

## Phase 4 — profile — DONE 2026-07-16

230 tests total in the suite after this phase, all green (`./mvnw test`, ~50s). 88 new profile
tests (41 + 27 + 5 + 6 + 9) across 5 classes; no main-source changes.

- [x] `profile/internal/ProfileServiceImplTest` (41) — pure unit, all repos/`ReportService`/`BanCache`
      mocked. getProfile: own-profile dto + not-found. existsByUsername delegate. completeOnboarding:
      happy path (username/bio/location set, onboarding flag cleared, replace-all cat/role deletes,
      default prefs saved), username-taken pre-check short-circuits the save, DB `DataIntegrityViolation`
      → `UsernameAlreadyTaken` fallback, unknown city / unknown car-category → `InvalidReference`,
      missing profile → 404. getPublicProfileByUsername + not-found. searchByUsername blank/null →
      empty without querying, else maps to search dtos. findIdByUsername / findByIds / findBusinessProfileIds
      empty-input guards + delegation. findModerationSnapshot field mapping + empty-when-gone.
      ban/unban set state + `banCache.evict`, missing profile → 404 (no evict). listCountries mapping.
      updateLocation applies city+radius/saves, empty request leaves fields + never queries city,
      unknown city → 400. updateRealtimeLocation stores a JTS point in (lng,lat) order, missing → 404.
      setCarCategories/setCommunityRoles `requireProfile` guard + replace-all de-dup + unknown-id 400;
      get*Categories/Roles hydrate stored ids. Notification prefs: stored row read (no save), lazy
      default-create when missing, 404 when no row and no profile, update replaces every toggle +
      stamps updatedAt. reportProfile resolves username + delegates, self-report → `CannotReportSelf`,
      unknown username → 404 (both without touching `ReportService`).
- [x] `profile/internal/controller/ProfileControllerWebTest` (27) — `@AppWebMvcTest(ProfileController.class)`
      + `@MockitoBean ProfileService`. 401 unauthenticated; JWT-subject delegation; snake_case `ProfileDto`
      shape (`avatar_url`, `followers_count`, `is_verified`, `is_business`, `requires_onboarding`,
      `city_id`, `discovery_radius_km`); onboarding 200 + captured body, 409 username-taken, bean-validation
      400s (blank username / bad pattern / missing city / out-of-range radius / empty category_ids) all
      short-circuit the service; by-username public dto (no `requires_onboarding`) + 404; exists boolean;
      search delegates `q` + snake_case results, missing `q` → 400; location 200 + invalid-city 400 +
      out-of-range radius 400; realtime-location void→200 + captured lat/lng, missing-lat / out-of-range
      400; car-categories + community-roles get/put; notifications snake_case toggles + full-payload put +
      missing-toggle 400.
- [x] `profile/internal/controller/ProfileReferenceDataControllerWebTest` (5) — 401 unauthenticated;
      countries/community-roles/car-categories lists; cities delegates the `{countryId}` path variable
      and exposes snake_case `country_id` + `lat`/`lng`.
- [x] `profile/internal/controller/ProfileReportControllerWebTest` (6) — `@MockitoBean` both
      `ProfileService` + `ReportService`. 401 unauthenticated; report-reasons passthrough; file-report
      204 with JWT-subject reporter + captured `reason_id`; body-less report → 204 with null reason;
      self-report → 400, unknown username → 404.
- [x] `profile/internal/repository/ProfileRepositoryIT` (9) — `@DataJpaTest` +
      `@AutoConfigureTestDatabase(replace = NONE)` + `AbstractPostgresIT`, JdbcTemplate fixtures
      (auth.users + profiles). findByUsername hit/miss; prefix search case-insensitive + collation-ordered;
      findAllByIdIn subset; `findBusinessIds` business-only; `findBanState` projection (banned+until,
      and unbanned reads false/null); username UNIQUE constraint rejects a duplicate (the onboarding
      fallback's premise); `countByIdIn` counts only existing car-category options; car-category junction
      round-trips via `saveAll`/`findByIdProfileId` and clears via `deleteByIdProfileId`.

**Skipped as legacy-privacy:** none found — the profile module has no private-account gating code
(consistent with the "all accounts public" memory note); every profile read is unconditionally public.

**Untestable without main-code changes:** none — every profile class is reachable via package-private
test classes (`profile.internal`, `.controller`, `.repository`). `BanCache` + `BannedUserInterceptor`
were already covered in Phase 1 and are not re-tested. Reference/junction/notification derived-query
repos are exercised indirectly through the service unit tests and the one representative
`countByIdIn` + junction IT rather than a dedicated IT each (low-risk Spring Data derivations).

**Profile bugs / divergences found (reported, not fixed):**
- **Bio length is triply inconsistent.** `OnboardingRequest.bio` validates `@Size(max = 500)`,
  `ProfileEntity.bio` maps `length = 2000`, but the live schema (`schema.sql`) has
  `CONSTRAINT profiles_bio_check CHECK (length(bio) <= 100)`. A bio of 101–500 chars passes bean
  validation, then `saveAndFlush` throws a `DataIntegrityViolationException` — which
  `completeOnboarding` **catches and rethrows as `UsernameAlreadyTakenException`**, so a too-long bio
  surfaces to the client as a bogus 409 "username already taken". Needs the three limits reconciled
  (and the catch narrowed to genuine username collisions).
- **`ProfileEntity.toDto()` NPEs on a city-less profile.** It calls `city.getId()` with no null guard,
  yet `city_id` is nullable and a freshly-created (pre-onboarding) profile has none. `getProfile` /
  `updateLocation` would throw NPE (→ 500) for any profile whose city was never set. Not reachable in
  the current happy path (onboarding requires a city), but fragile — `updateLocation` with an
  all-null `LocationRequest` on a city-less profile would trip it.

## Phase 5 — follow (the `relationships` module) — DONE 2026-07-16

264 tests total in the suite after this phase, all green (`./mvnw test`, ~55s). 34 new follow tests
(17 + 10 + 7) across 3 classes; no main-source changes. Note: the "follow" feature lives in the
`relationships` module (owns `public.follows`), not a `follow` package.

- [x] `relationships/internal/RelationshipServiceImplTest` (17) — pure unit, mocked
      `RelationshipRepository` + `ProfileService`. follow: persists an accepted row (captured id =
      follower/following, status `accepted`) returning ACCEPTED; self-follow guard fires *before* any
      repo touch (findById never called); unknown username → `ProfileNotFoundException` with no save;
      re-following is idempotent (returns the existing row's status, never saves); an existing
      non-`accepted` row maps to NOT_FOLLOWING (documents `toStatus`). unfollow: deletes when present,
      no-op when absent, unknown username → 404. getFollowStatus: ACCEPTED when a row exists,
      NOT_FOLLOWING when absent. getFollowers/getFollowing: hydrate in query order and set the per-row
      `isFollowing` flag from the single `findAcceptedFollowingIdsIn` bulk query; empty follower list
      short-circuits (no `findByIds`, no follow-state query); list entries whose profile fails to
      hydrate are filtered out. removeFollower: deletes the row addressed as (otherUser → caller),
      no-op when they aren't a follower, unknown username → 404 (no repo interaction).
- [x] `relationships/internal/RelationshipControllerWebTest` (10) — `@AppWebMvcTest(RelationshipController.class)`
      + `@MockitoBean RelationshipService`. 401 unauthenticated; POST `/{username}` 200 + `FollowStatusDto`
      enum shape (`$.status` = `"ACCEPTED"` / `"NOT_FOLLOWING"`, Jackson serializes the enum by name)
      with JWT-subject + username delegation; self-follow → 400, unknown username → 404 (both from the
      exception→status mapping); DELETE `/{username}` 204 delegation; GET `/{username}/status` shape;
      DELETE `/followers/{username}` 204 delegation + unknown-username 404; GET `/{username}/followers`
      + `/following` snake_case `FollowProfileSearchResult` list (`avatar_url`, and the boolean
      `is_following` — `isFollowing` keeps its component name per the Jackson gotcha).
- [x] `relationships/internal/RelationshipRepositoryIT` (7) — `@DataJpaTest` +
      `@AutoConfigureTestDatabase(replace = NONE)` + `AbstractPostgresIT`, JdbcTemplate fixtures
      (auth.users + profiles + follows rows with explicit `created_at`). `findAcceptedFollowerIds` /
      `findAcceptedFollowingIds` return newest-first (createdAt desc); `findAcceptedFollowingIdsIn`
      returns only the followed subset; the `status = 'accepted'` filter excludes a row flipped to
      `pending` after insert; duplicate follow pair → `DuplicateKeyException` (composite PK); a
      self-follow row → `DataIntegrityViolationException` (`no_self_follow` CHECK); the
      `handle_follow_change` AFTER trigger increments `following_count`/`followers_count` on INSERT and
      decrements on DELETE.

**Skipped as legacy-privacy:** the `pending` follow status and the whole request/approval path.
`FollowStatus` has only ACCEPTED/NOT_FOLLOWING, but the DB `follows_status_check` still permits
`pending`, the repository queries all filter `status = 'accepted'`, and `handle_follow_change`
condition 2 handles a pending→accepted transition — all vestigial private-account machinery that the
"all accounts public" direction is refactoring out. I tested only that the `accepted` filter works
(via a post-insert flip to `pending`) and did NOT write tests cementing a request/approval flow.

**Unused / dead code (reported, not tested):** `relationships/internal/BlockedAccountEntity` +
`BlockedId` map a `blocked_accounts` table but are `public`, have zero references anywhere in the
codebase, and no service/controller/repository touches blocking. They carry no behavior to test
(pure `@Getter`/`@Setter` JPA mappings) — flagged as apparently-orphaned scaffolding for a
not-yet-built block feature (the DMs memo also lists "blocks" as deferred). Being `public` in an
`internal/` package with no consumers is a minor Modulith smell.

**No follow bugs found.** Minor note (not a bug): the service sets `status = "accepted"` in Java on
insert *and* the `set_initial_follow_status` BEFORE INSERT trigger forces it again — belt-and-braces,
harmless, and intentional per the entity Javadoc (keeps the returned status correct without a re-read).

## Phases 6–13

_Expand each when its turn comes (see recipe + table in the roadmap)._

## Decisions / gotchas log

Record anything learned mid-work that changes the plan (e.g. how the auth.users stub ended up
looking, PG version pinned, flaky-test fixes), newest first.

- 2026-07-16 — Phase 5 gotchas:
  - The "follow" feature is the `relationships` module (package
    `com.tweakdapp.backend.relationships`, owns `public.follows`) — there is no `follow`
    package. REST base path is still `/api/v1/follow`.
  - The `follows` BEFORE INSERT trigger `set_follow_initial_status` force-sets `status := 'accepted'`
    on *every* insert, so you can't insert a `pending` row directly. To exercise a repository's
    `status = 'accepted'` filter, insert first (row lands accepted) then `UPDATE ... SET status =
    'pending'` — the AFTER trigger's counter logic only reacts to pending→accepted, so this flip
    doesn't disturb the counters.
  - `follows` has both FKs to `public.profiles(id)` (follower + following), a composite PK
    (follower_id, following_id) → duplicate pair surfaces as Spring `DuplicateKeyException`, and a
    `no_self_follow` CHECK (follower_id <> following_id) → self-follow row surfaces as
    `DataIntegrityViolationException`. IT fixtures must create a profile for *both* ids before
    inserting a follow.
  - `FollowProfileSearchResult.isFollowing` (record boolean component) serializes to `is_following`
    — same "Jackson keeps the `isX` component name" rule as the profile DTOs. `FollowStatusDto.status`
    is a plain enum → serialized by name (`"ACCEPTED"` / `"NOT_FOLLOWING"`).

- 2026-07-16 — Phase 4 gotchas:
  - `profiles.username` has a case-insensitive collation in the live schema: the derived
    `findTop20ByUsernameStartingWithIgnoreCaseOrderByUsernameAsc` orders `carla` **before** `Carlos`
    (not ASCII order where uppercase `C` < lowercase `c`). Assert the collation order, not the
    codepoint order.
  - Record boolean components named `isX` (e.g. `ProfileDto.isVerified`, `isBusiness`,
    `requiresOnboarding`) serialize with the snake_case strategy off the **component name**, so the
    JSON keys are `is_verified` / `is_business` / `requires_onboarding` — Jackson does NOT strip the
    `is` prefix for records. But note `ProfileModerationSnapshotDto` deliberately names its components
    `business` / `banned` (no `is` prefix), so its accessors are `.business()` / `.banned()`.
  - An empty-body POST maps a `@RequestBody(required = false)` to `null` (verified via the
    `ProfileReportController` 204-null-reason path) — no need to send `{}`.
  - Inserting a `profiles` row in an IT fires the `on_profile_created_create_garage` AFTER INSERT
    trigger; it's harmless against the dumped schema (same as the dms/presence ITs), so JdbcTemplate
    `insert into public.profiles (id, username)` still works with DB defaults filling the NOT-NULL
    `bio`/`role`/`external_link` columns the entity would otherwise send as NULL.

- 2026-07-16 — Phase 3 gotchas:
  - The registry/lifecycle read `Instant.now()` directly (no injectable `Clock`). Grace-window logic
    stays fully deterministic without touching the clock: `PresenceRegistry.sweepExpired(grace)`
    computes `cutoff = now.minus(grace)`, so a **negative** duration puts the cutoff in the future
    (every pending stamp is before it → forced expiry) and a **large positive** one puts it in the
    past (nothing expires). Lifecycle instants are verified with `any(Instant.class)` / captured and
    cross-checked (offline event's last-seen == the upserted instant), not compared to wall-clock.
  - `PresenceSessionListener` listens for `SessionConnectedEvent` (session id read from the message
    headers via `SimpMessageHeaderAccessor.getSessionId`) and `SessionDisconnectEvent` (session id
    from the constructor arg, `event.getSessionId()`). Build both with `new SessionConnectedEvent(src,
    msg, principal)` / `new SessionDisconnectEvent(src, msg, sessionId, CloseStatus.NORMAL, principal)`
    where `msg` is a `StompHeaderAccessor.create(CONNECTED)` frame with `setSessionId(...)`; a
    `Principal` is just `id::toString` (its `getName()` must be the user UUID string).
  - pgjdbc reads a `timestamptz` column straight into `Instant` via `queryForObject(sql, Instant.class,
    ...)`, and equals a zero-sub-second `Instant.parse("...Z")` — no `OffsetDateTime` round-trip
    needed for last-seen assertions. Native `@Modifying` upserts execute on the shared `@DataJpaTest`
    connection, so a JdbcTemplate read in the same test sees them (and both roll back).
  - Controller `@RequestParam List<UUID>` splits a single comma-joined value (`?user_ids=a,b`) into a
    2-element list via the default conversion — MockMvc `.param("user_ids", A + "," + B)` exercises the
    real `?user_ids=a,b,c` contract. A missing required param surfaces as 400 (after security, so the
    request still needs a JWT).

- 2026-07-16 — Phase 2 gotchas:
  - Spring Boot 4 relocated the test-slice annotations into per-module autoconfigure jars: `@DataJpaTest` is now `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` (spring-boot-data-jpa-test) and `@AutoConfigureTestDatabase` is `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase` (spring-boot-jdbc-test) — NOT the old `org.springframework.boot.test.autoconfigure.*` paths.
  - Controller-slice service mocks use `@MockitoBean` from `org.springframework.test.context.bean.override.mockito.MockitoBean` (Spring 7; the old `@MockBean` is gone).
  - `profiles.username` has a UNIQUE constraint — IT fixtures inserting several profiles must give each a distinct username (deriving it from a UUID *prefix* collides because the test UUIDs share the `00000000` prefix; use the full UUID).
  - `@DataJpaTest` rolls back per test, so a `new JdbcTemplate(dataSource)` built in `@BeforeEach` shares the test's transaction/connection — rows it inserts are visible to the repository queries and vanish on rollback. No triggers on the `dm_*` tables, so raw counts are trustworthy here.

- 2026-07-16 — Phase 1 gotchas:
  - Controller slice tests: use `@AppWebMvcTest(TheController.class)` (in `testsupport`), never raw `@WebMvcTest`. The slice picks up `BannedUserInterceptor` + its `Registration` but not `BanCache` — the composed annotation imports `SecurityConfig` + `BanEnforcementTestStub` (Mockito `BanCache`, unbanned by default) so contexts load and security rules are real.
  - Jackson is v3 here: `tools.jackson.databind.ObjectMapper`, NOT `com.fasterxml.*`.
  - `FACTOR_BEARER` authority (Spring Security 7) shows up alongside `ROLE_*` — filter before asserting.

- 2026-07-16 — Phase 0 gotchas, for anyone touching the infra:
  - **Docker Desktop must be running** for IT classes and for `./scripts/dump-schema.sh` (`open -a Docker` if not).
  - pg_dump ≥17.6 emits `\restrict`/`\unrestrict` psql guards that the postgis image's psql rejects — the dump script strips them (also strips `CREATE SCHEMA public`, RLS policies, superuser SET lines).
  - PostGIS is installed in the **public** schema on the live Supabase project (`public.geography`), so the preamble creates it there, not in an `extensions` schema.
  - `auth.users` stub table + `auth.uid()`/`auth.role()` stubs are prepended by the script; the real `handle_new_user` trigger is NOT recreated — fixtures insert `auth.users` + `profiles` rows directly.
  - One flaky full-suite failure (`NoSuchBeanDefinitionException: JwtAuthenticationConverter`) was caused by IntelliJ recompiling `target/classes` mid-test-run (timestamps matched the IDE app launch exactly). Not a real bug; don't chase it. Avoid running `./mvnw test` while the IDE is building.
- 2026-07-16 — Roadmap created. DB strategy: Testcontainers + schema dump. Priority: dms + presence first (owner-confirmed).
