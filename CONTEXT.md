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

The project uses **Spring Modulith** to enforce hard module boundaries at test time. Each top-level package under `com.tweakdapp.backend` is an `@ApplicationModule` declared in its `package-info.java`.

### Module layout

```
com.tweakdapp.backend/
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
├── feedbackfeed/     ← public API: FeedbackFeedService, DTOs. The in-app community feedback board
│   └── internal/     ← private: controller, service impl, entities, repositories, dual-shape keyset cursor
├── notification/     ← public API: NotificationService, DTOs. In-app notifications + FCM push
│   └── internal/     ← private: controller, service impl, entities, repositories, producer listeners
│       └── push/     ← private: device registry (endpoints + entity), Firebase config, FCM sender, dispatcher
├── reputation/       ← public API: ReputationService (award SPI + reads), DTOs, reason-code constants
│   └── internal/     ← private: controller, service impl, entities (reason catalogue / history), repositories, keyset cursor
├── badges/           ← public API: BadgeService (award SPI + reads + admin ops), DTOs, badge-code constants
│   └── internal/     ← private: read-only controller, service impl, entities (catalogue / unlocks), repositories
│                       depends on NOTHING but storage+shared, so `profile` can embed badges in its response
└── shared/           ← OPEN module: security config, realtime (STOMP WebSocket at /ws), exception hierarchy, global handler, moderation + staff SPIs
    └── geo/          ← app-wide geo reference data: cities + countries entities/repos/DTOs, GeoSupport (lat/lng ↔ JTS Point)
```

**Public root package** — what other modules may import: service interfaces, DTOs/records, domain events, exception types.

**`internal/` subpackage** — implementation details. Modulith treats these as package-private to the module. Never make internal types `public` just to work around a cross-module access error; that defeats the boundary.

**`shared/`** is declared `type = ApplicationModule.Type.OPEN`, so any module can import anything from it. Keep it limited to genuine cross-cutting concerns.

Module boundaries are verified by `ModularityTests`. A violation fails that test, not the compiler.

## Security

`shared/security/SecurityConfig.java` configures stateless JWT authentication using Supabase as an OAuth2 resource server.

- All endpoints require authentication unless under `/public/**`. Exactly one route lives
  there today: `GET /public/v1/cars/{code}`, the public car page behind a share link
  (see *Car sharing*). Anything added under `/public/**` is on the open internet — it must
  return a hand-written projection, never a DTO the app happens to share, and it must apply
  the ban check itself, because `BannedUserInterceptor` only sees authenticated requests.
  The WebSocket handshake at `/ws/**` is also `permitAll` — the real authentication happens
  at the STOMP CONNECT frame (`shared/realtime/JwtChannelInterceptor`, same JWT validation
  as REST).
- `/api/v1/admin/**` requires `ROLE_ADMIN` (Supabase `app_metadata.role = 'admin'`); the `admin`
  module then applies fine-grained team-role capability checks (`admin_team_members`). Approving
  user-submitted map events is gated on `APPROVE_EVENTS`, and managing the community feedback feed
  (roadmap status, official response, removal) on `MANAGE_ROADMAP` — both held by `owner` and
  `senior_admin` only.
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

## Push notifications

Push is a second delivery channel for the rows the `notification` module already writes, not a
separate system — `data.notification_id` is the `notifications.id` the client marks read. Delivery is
split by ownership:

- **This backend** sends every notification type except `dm`, via `firebase-admin` from
  `notification/internal/push`. Credentials come from Application Default Credentials (the Cloud Run
  runtime service account); if they cannot be resolved the app still starts and simply does not push.
- **A Supabase edge function** sends `dm`. DMs never reach Spring on the write path — the Flutter
  client calls the `dm_send_message` Postgres RPC directly and live delivery rides Supabase Realtime
  — so there is no Spring event to hook.

Both read device tokens from the one shared table `user_devices_firebase_token`, which is
RLS-denied and revoked from `anon`/`authenticated`: only the backend (table owner) and the edge
function (`service_role`, select + delete) can see it. FCM tokens are device-addressable secrets and
are never returned by any endpoint.

## Reputation

`profiles.reputation_score` is owned by the **profile** module; the itemised timeline behind it
(`reputation_score_history`) and the reason catalogue (`reputation_score_reason_options`) are owned
by **reputation**. The seam is `ProfileService.applyReputationDelta`, which moves the score under a
pessimistic write lock and reports the before/after pair for the history row — so no module writes
another's table, and `previous_score + score_gain = new_score` holds on every row.

There is deliberately **no database trigger**. A trigger would compute its own before/after, and two
concurrent awards could record the same `previous_score`; the row lock is what serialises them.
Callers must award inside the achievement's own transaction so the points and the thing that earned
them commit together.

Awards carry a `source_type`/`source_id`/`source_label` triple identifying what earned them. It is
**not** a foreign key — a history entry has to outlive the event or car behind it, and sources are
polymorphic across too many tables for one FK column each. `source_label` is snapshotted at award
time, so the reputation module never reads another module's tables to render a timeline. A partial
unique index over live rows makes sourced awards exactly-once, so callers need no dedup guard.

Awards are revocable (`revoked_at`), for facts retracted after payout. Revoked entries are visible
to the owner on `/me/history` but **omitted entirely** from the public
`/users/{username}/history` — surfacing them to strangers would make the timeline a shaming
mechanic. Summary aggregates count live entries only, so the breakdown always matches the score.

Reputation is never granted over HTTP: the module's endpoints are all reads, and awarding is
SPI-only from the module that witnessed the achievement.

## Badges

Badges sit next to reputation and behave differently on purpose. Reputation is a running total with
a ledger behind it; a badge is a **current-state fact** — you hold it or you don't. So there is no
history table, no points, and no revocation tombstone: taking a badge back deletes the row, and the
badge becomes earnable again.

`badges` is an admin-curated catalogue; `user_badges` records who holds what. A unique index on
`(user_id, badge_id)` is what makes `BadgeService.award` exactly-once, so callers fire it from a
retried listener with no guard of their own — the same "the guarantee is in the database" reasoning
as reputation's source index.

The badge artwork lives in the Cloudflare R2 `app-assets` bucket. The database stores **object
keys**; the backend builds the full public URLs at read time through `StorageService`, exactly as it
does for avatars and post images. Each badge has an unlocked variant and an optional locked one.

Two ways a badge is granted, and neither is reachable by the user receiving it: automatically via
the SPI from the module that witnessed the achievement, or by hand from the dashboard
(`MANAGE_BADGES`, owner and senior admin only), which stamps the granting staff member on the row.
That granter is **staff-only** — the app-facing DTO has no field for it, and only the dashboard's
holder read returns it.

A user's earned badges are **embedded in the profile response** (`ProfileDto` / `PublicProfileDto`
each carry a `badges` array), so the profile screen paints its badge row with the header. That is
what fixes the dependency direction: `profile` → `badges`, and therefore `badges` reads nothing from
`profiles` — no username-keyed endpoint, no existence check on award. The badges a user has *not*
earned are their own separate read (`/api/v1/badges/me/locked`), for the locked section of their own
profile only.


## Car sharing

An owner shares a car as `https://web.tweakdapp.com/c/{code}` and as a printable QR code that
encodes the same URL plus `?s=qr`. Scanning it opens the app when installed (Universal
Links / App Links on `/c/*`) and the public web page otherwise. It lives in the `garage`
module, in `car_share_links`.

The host is the **`web.` subdomain**, not the apex: `tweakdapp.com` is the presentation site
(Cloudflare Pages, repo `Tweakd Website`) and knows nothing about share codes, while
`web.tweakdapp.com` is a separate Cloudflare Worker (repo `Tweakd-Web-App`) that exists only
to render this page. `sharing.public-base-url` must always match the host the app claims for
deep links — the iOS Associated Domains entitlement and the Android intent filter — or links
open a browser instead of the app.

The one thing that shapes every decision here: **the code can end up printed on a sticker
glued to a car**, so it has to keep meaning what it meant when it was printed.

- The code is opaque, random and immutable — 10 Crockford-base32 characters, no `I L O U`.
  It encodes nothing renameable, so changing a username or a model does not break a sticker.
  Lookups normalise (uppercase, `O→0`, `I/L→1`, dashes stripped), so a code read off a
  scratched sticker and typed in by hand still resolves.
- Minting is idempotent and there is **no regenerate**. Owners get pause / resume, which keeps
  the code: a resumed link brings an already-printed sticker back to life. Only system events
  revoke — car transfer, and cascades on car or account deletion.
- The QR is rendered server-side as **SVG** (ZXing `core`, error correction H), so a reprint
  years later is byte-identical and prints at any size.
- The public read is a hand-written projection (`PublicCarDto`), never `CarDto`. A new car
  field is public only if somebody adds it to the projection on purpose.
- Views and QR scans are counted separately from the `?s=` tag; known link-preview crawlers
  are served but not counted.
- The `web.` Worker fetches the public endpoint at the edge and renders finished HTML with the
  `og:*` tags already in it — link-preview crawlers run no JavaScript, and a share that does not
  unfurl loses most of its taps. Browsers therefore never call this backend cross-origin, which
  is why it needs no CORS configuration at all.

**Whoever implements car transfer must call `GarageService.revokeShareLinksForCar(carId)` in
the transfer transaction**, or a sticker on a sold car keeps pointing strangers at the new
owner's build.


## REST conventions

- Base path: `/api/v1/<module>/...`
- Controllers stay thin: extract `userId` from the JWT, delegate to the module's service interface, return DTO records.
- Error responses follow the `ErrorResponse` shape defined in `shared/exception/`.

## Lombok

Entities use `@Getter` / `@Setter`. The Lombok annotation processor is wired in `pom.xml`; Lombok is excluded from the runtime jar.

## Module READMEs

Each module has a `README.md` with its specific API surface, endpoints, entities, exceptions, and Supabase trigger dependencies:

- [`profile` module](src/main/java/com/tweakdapp/backend/profile/README.md)
- [`follow` module](src/main/java/com/tweakdapp/backend/relationships/README.md)
- [`garage` module](src/main/java/com/tweakdapp/backend/garage/README.md)
- [`posts` module](src/main/java/com/tweakdapp/backend/posts/README.md)
- [`feed` module](src/main/java/com/tweakdapp/backend/feed/README.md)
- [`tags` module](src/main/java/com/tweakdapp/backend/tags/README.md)
- [`business` module](src/main/java/com/tweakdapp/backend/business/README.md)
- [`mapevents` module](src/main/java/com/tweakdapp/backend/mapevents/README.md)
- [`forums` module](src/main/java/com/tweakdapp/backend/forums/README.md)
- [`report` module](src/main/java/com/tweakdapp/backend/report/README.md)
- [`feedback` module](src/main/java/com/tweakdapp/backend/feedback/README.md)
- [`feedbackfeed` module](src/main/java/com/tweakdapp/backend/feedbackfeed/README.md)
- [`notification` module](src/main/java/com/tweakdapp/backend/notification/README.md)
- [`reputation` module](src/main/java/com/tweakdapp/backend/reputation/README.md)
- [`badges` module](src/main/java/com/tweakdapp/backend/badges/README.md)
- [`support` module](src/main/java/com/tweakdapp/backend/support/README.md)
- [`dms` module](src/main/java/com/tweakdapp/backend/dms/README.md)
- [`presence` module](src/main/java/com/tweakdapp/backend/presence/README.md)
- [`admin` module](src/main/java/com/tweakdapp/backend/admin/README.md)
- [`storage` module](src/main/java/com/tweakdapp/backend/storage/README.md)
- [`shared` module](src/main/java/com/tweakdapp/backend/shared/README.md)

The admin-dashboard build-out (modules `notification` / `support` / `admin`, feedback board,
moderation, bans) is documented end-to-end in [`ADMIN_DASHBOARD_PROGRESS.md`](ADMIN_DASHBOARD_PROGRESS.md).
