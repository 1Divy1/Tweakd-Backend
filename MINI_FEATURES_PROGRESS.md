# Mini-Features Batch — Progress Tracker

Session started 2026-07-17. Orchestration: Fable plans/reviews, Opus subagents write code.
If resuming: read this file top to bottom, check the status table, continue with the first
non-DONE item. Verify claims against the working tree (`git status`, `./mvnw -q compile`).

## Status

| # | Item | Status | Agent |
|---|------|--------|-------|
| 0 | Investigation + user decisions | DONE | Fable |
| 1 | Supabase migrations (language FK, bio 500, dm_message_car_tags) | DONE | Fable |
| 2 | Schema dump refresh (`./scripts/dump-schema.sh`, needs Docker) | DONE | Fable |
| 3 | Language feature (profile module) + profile edit (name/bio/avatar) | DONE | Opus agent A |
| 4 | DM car tagging (dms module) | DONE | Opus agent B |
| 5 | Notification producers (posts/forums events → notification listeners) | IN PROGRESS | Opus agent C |
| 6 | Fable code review + security review of the whole diff | NOT STARTED | Fable |
| 7 | Mobile API templates handed to user | NOT STARTED | Fable |

## Investigation results (all verified against live DB + code on 2026-07-17)

- **Saved posts: ALREADY IMPLEMENTED.** `GET /api/v1/posts/saved` (cursor keyset, own saves only).
  `saved_posts.post_id → posts.id ON DELETE CASCADE` confirmed — author-deleted posts vanish. No work.
- **DM unread badge: ALREADY IMPLEMENTED.** `GET /api/v1/dms/unread-count` → `{"unread": n}`. No work.
- **Forum reply report-reasons bug: backend is CORRECT** (endpoint `GET /api/v1/forums/replies/report-reasons`,
  `ReportTarget.forum_thread_reply` matches DB enum `target_entity` label, 12 seeded rows).
  Root cause almost certainly the MOBILE app calling the pre-2026-07-04 path `/forums/posts/report-reasons` (404 now).
  → User must fix the URL in Flutter. Nothing to change in backend.
- **Signup was at risk:** `profiles.app_language` default was `''` + FK to `app_language_options.language`
  and `handle_new_user` doesn't set the column → next signup would hit an FK violation. Fixed by migration #1.
- Notification module already has read API (`GET /api/v1/notifications`, `/unread-count`, mark-read) + `push()`.
  Missing part = producers. No module currently depends on notification from posts/forums/profile side → listeners
  in `notification.internal` importing event records from posts/forums public APIs create no Modulith cycle.
- RLS enabled on all public tables; new tables must `ENABLE ROW LEVEL SECURITY` (backend connects as owner, bypasses it).
- `dm_messages.content` is NOT NULL, no CHECK — empty string allowed for cars-only messages.

## User decisions (2026-07-17)

1. **Language**: FK moves to `app_language_options.id` (`'ro'`/`'en'`); profiles store codes; default `'en'`;
   backfill `'English'→'en'`. Mobile sends/receives codes.
2. **Notifications v1 scope** (all gated by recipient's `notification_preferences`, never self-notify):
   - post likes (`likes_enabled`), post comments (`comments_enabled`), post shares (`shares_enabled`)
   - forum thread replies + replies-to-my-reply (`comments_enabled`)
   - forum thread likes + reply likes (`likes_enabled`)
   - **NO DM notifications** in the notifications page — DMs use the existing unread-count badge.
   - flash_meets / price_drops / organized_events: no backing feature yet, skip.
3. **DM car tags**: cars-only messages allowed — content optional when ≥1 car tagged (store `''`).
   A message must have text OR ≥1 tagged car. Blank content + not deleted ⇒ client renders "shared cars".
4. **Bio limit: 500 chars** everywhere (DB CHECK widened from 100, entity, request validation).

## Fable-decided defaults (flag to user in final summary)

- Name edit: optional in PATCH, trimmed, max 80 chars, may be cleared to empty.
- DM tags: max 10 cars per message; tagged cars returned as garage `CarSummaryDto` (mirrors posts).
- Deleted car ⇒ CASCADE removes tag from old DM messages silently.
- Avatar: new `AVATARS` R2 bucket following the garage/posts pattern (persist key, build public URL on read).
  Read fallback: values starting with `http` (Google photos) are returned as-is.
  **USER ACTION NEEDED: create the R2 bucket + add `CLOUDFLARE_AVATARS_BUCKET_NAME` and
  `CLOUDFLARE_AVATARS_BUCKET_URL` to `.env` before booting the app.**
- Notification aggregation: none in v1 (one row per event); like→unlike→re-like makes a duplicate. Future work.
- Notification `type` values: `post_like`, `post_comment`, `post_share`, `forum_thread_reply`,
  `forum_reply_reply`, `forum_thread_like`, `forum_reply_like`. Payload carries actor id/username + target ids.

## Migrations (Fable applies via Supabase MCP, project fybgmaigzidhbmhbgfhu "Tweakd")

1. `fix_app_language_fk_and_default` — drop `profiles_app_language_fkey`, backfill codes,
   default `'en'`, re-add FK → `app_language_options(id)` ON UPDATE CASCADE ON DELETE RESTRICT.
2. `widen_profiles_bio_check_to_500` — recreate `profiles_bio_check` as `length(bio) <= 500`.
3. `create_dm_message_car_tags` — `(message_id FK dm_messages CASCADE, car_id FK cars CASCADE,
   created_at, PK(message_id, car_id))`, index on car_id, RLS enabled.

## Gotchas for subagents

- Java 25, Spring Boot 4.0.6, Modulith 2.0.3, Jackson 3 (`tools.jackson`), global SNAKE_CASE wire format.
- `ddl-auto: validate` — schema owned by Supabase; migrations already applied before agents run.
- Never touch `.env` or the live DB. Tests: Testcontainers (Docker must run), schema from
  `src/test/resources/db/schema.sql`. Use `AbstractPostgresIT`, `TestJwts`, `@AppWebMvcTest` (never raw `@WebMvcTest`).
- Module boundaries: root pkg = public API (service interface + `dto/` with `@NamedInterface("dto")`),
  `internal/` package-private. Never make internal types public.
- Controllers thin; JWT subject = profile UUID; base path `/api/v1/<module>`.
- Verify: `./mvnw -q compile`, `./mvnw test -Dtest=ModularityTests#verifiesModularStructure`, module tests green.
- Update this file's status table + a short work log entry when done. No commits.

## Work log

- 2026-07-17: Investigation + user Q&A done. Tracker created.
- 2026-07-17 (Fable review of agent A): added avatar-key ownership check in ProfileServiceImpl.updateAvatar
  (key must start with `avatars/{userId}/`, else 400) + test. Without it a caller could point their profile at —
  and later after-commit-delete — another user's R2 object. Posts has the same gap (savePostImageKeys accepts
  arbitrary keys) — flagged for the final security review, not fixed here.
- 2026-07-17 (Item 4 — DM car tagging): DONE. Full suite green: **308 tests** (was 293), 0 failures.
  `ModularityTests#verifiesModularStructure` green — dms → garage created no cycle (garage does not
  depend on dms).

  **Endpoint changes** (`DmsController`, base `/api/v1/dms`, snake_case wire format):
  | Method | Path | Request (JSON) | Response (JSON) |
  |---|---|---|---|
  | POST | `/messages` | `{"recipient_id":"<uuid>", "content":"hi"?, "tagged_car_ids":["<uuid>", …]?}` | 201 `DmMessageDto` (now with `"tagged_cars":[CarSummaryDto…]`) |
  | GET | `/conversations/{id}/messages` | — | each `items[]` message now carries `"tagged_cars":[…]` |

  A `DmMessageDto` now serializes as:
  `{"id","conversation_id","sender_id","content","deleted","created_at","tagged_cars":[{"id","brand","model","cover_image","status","owner"}]}`.
  `tagged_cars` is `[]` when none, and always `[]` once the message is deleted.

  **Cars-only rule:** `content` is optional — a message is valid with non-blank text OR ≥1 tagged
  car. Car-only ⇒ stored `content=''` and a blank chats-list preview (client renders "shared cars").
  New 400s (dms exceptions → `GlobalExceptionHandler`): `EmptyMessageException` (neither),
  `TooManyTaggedCarsException` (>10), `TaggedCarNotFoundException` (unknown car). Ids de-duplicated
  silently. `content` still `@Size(max=2000)`. Deleted car ⇒ ON DELETE CASCADE removes the tag from
  old messages silently.

  **Files created:** `dms/internal/entities/DmMessageCarTagEntity.java`, `DmMessageCarTagId.java`,
  `dms/internal/repositories/DmMessageCarTagRepository.java`, `dms/exception/EmptyMessageException.java`,
  `TooManyTaggedCarsException.java`, `TaggedCarNotFoundException.java`;
  test `dms/internal/repositories/DmMessageCarTagRepositoryIT.java`.
  **Files modified (main):** `dms/dto/SendMessageRequest.java` (content optional + `taggedCarIds`),
  `dms/dto/DmMessageDto.java` (+`taggedCars`), `dms/internal/DmsServiceImpl.java`
  (GarageService + DmMessageCarTagRepository injected; validate/persist/assemble),
  `dms/README.md`, `DMS_PROGRESS.md`.
  **Files modified (test):** `DmsServiceImplTest` (+8: text+tags, cars-only, both-empty 400, >10 400,
  dedup, unknown-car 400, page assembly no-N+1, deleted→empty), `DmsControllerWebTest` (+4 & repurposed
  the old blank-content test: cars-only send, empty 400, too-many 400, unknown-car 400, `tagged_cars`
  shape), `DmEventPusherTest` (message.created carries `tagged_cars`), `src/test/resources/db/seed.sql`
  (minimal car reference rows so an IT can insert a real `cars` row).
  **Test counts:** +15 net (DmsServiceImplTest 25→33, DmsControllerWebTest 15→19,
  DmMessageCarTagRepositoryIT +3; DmEventPusherTest count unchanged, one test enriched). Full suite 308/308.

  **Deviations / decisions to flag to user:**
  - The three car-tag validation failures are enforced in `DmsServiceImpl` as dms `BadRequestException`
    subclasses (→ 400), NOT bean-validation, so the messages are specific ("A message can tag at most
    10 cars", etc.). `content @Size(max=2000)` stays bean-validation. Consequence: the pre-existing
    web test `sendMessageWithBlankContentIsRejectedBeforeReachingTheService` no longer holds (blank
    content is now allowed with cars) — it was repurposed to assert blank content reaches the service
    and the empty-message rule fires there.
  - Max-10 is checked on the **de-duplicated** id set (15 copies of one id ⇒ 1 distinct ⇒ OK; 11
    distinct ⇒ 400).
  - Soft-delete returns empty `tagged_cars` by **excluding deleted messages from the tag lookup**; the
    `dm_message_car_tags` rows are left in place (only content is blanked, matching the existing
    soft-delete treatment). Nothing depends on those rows once the message is deleted.
  - `seed.sql` was extended with car reference rows (brand/model/color/drivetrain/distance-unit/status/
    fuel) because a `cars` row has 8 NOT NULL FKs and the schema-only dump seeds none; other module ITs
    don't read these lookup tables, so it's inert for them.

- 2026-07-17 (Item 3 — language + profile edit): DONE. Full suite green: **292 tests** (was 264), 0 failures. `ModularityTests#verifiesModularStructure` passes — profile → storage created no cycle (storage has `allowedDependencies = {}`, depends on nothing).

  **Endpoints added** (all under `ProfileController`, base `/api/v1/profile`; JWT subject = profile UUID):
  | Method | Path | Request | Response |
  |---|---|---|---|
  | GET | `/language-options` | — | `[{"id":"en","language":"English"}, …]` |
  | PATCH | `/me/language` | `{"language_id":"ro"}` | updated `ProfileDto` (200); unknown code → 400, blank → 400 |
  | PATCH | `/me` | `{"name":"…","bio":"…"}` (both optional) | updated `ProfileDto` (200); over-limit → 400 |
  | PATCH | `/me/avatar` | `{"key":"<r2 key>"}` | updated `ProfileDto` (200); blank key → 400 |
  | GET | `/api/storage/avatar` (`StorageController`) | — (JWT-scoped) | `{"key":"avatars/{userId}/{uuid}.webp","upload_url":"…"}` |

  **Deviations / decisions to flag to user:**
  - **`GET /api/v1/profile/language-options`** and **`PATCH /me/language`** live on `ProfileController`, NOT `ProfileReferenceDataController` (whose base is `/api/v1/profile/reference`). The task gave the literal path `/api/v1/profile/language-options` (no `/reference`), which only `ProfileController`'s base can produce; keeping both language endpoints together there also matches the `/me/*` grouping. `LanguageOptionDto` follows the reference-DTO record style (`{id, language}`).
  - **All four new mutating endpoints return the updated `ProfileDto`** (200), not 204. This mirrors the dominant module pattern (`/me/location`, `/me/car-categories`, `/me/community-roles`, `/me/notifications` all return updated state). The only void PATCH in the module is the fire-and-forget `/me/realtime-location`.
  - **Bio limit reconciled to 500 everywhere**: `ProfileEntity.bio` column length 2000 → 500; DB CHECK already 500 (migration #2); `OnboardingRequest.bio` was ALREADY `@Size(max=500)` (no change needed). New `ProfileEditRequest.bio` is `@Size(max=500)`. This resolves the triple-inconsistency bug flagged in TESTING_PROGRESS Phase 4.
  - **Name edit**: `ProfileEditRequest.name` trimmed in the record's compact constructor, `@Size(max=80)`, empty string allowed (clears). Absent/null field = unchanged (partial-update semantics for both name and bio).
  - **Avatar read-side resolution centralized** in new `profile/internal/ProfileDtoMapper` (`@Component`, holds `StorageService`): blank → passthrough (keeps null/`""`), `http…` → passthrough (legacy Google photos), else → `StorageService.publicUrl(AVATARS, key)`. Used by `ProfileDto`, `PublicProfileDto`, `ProfileSearchResultDto` (feeds posts/dms/forums author cards) AND the moderation snapshot's avatar. The old `ProfileEntity.toDto/toPublicDto/toSearchResultDto` methods were removed (logic moved to the mapper). The pre-existing `city.getId()` NPE-on-null-city bug was preserved verbatim in the mapper (out of scope, does not block).
  - **Avatar delete-after-commit**: `PATCH /me/avatar` registers a `TransactionSynchronization.afterCommit` that deletes the PREVIOUS R2 object only if it was a real key (non-blank, not `http…`) — copied from the posts module's delete pattern.
  - **New R2 `AVATARS` bucket**: added to `StorageBucket` enum + `R2Config` (`avatars` `BucketTarget` + `target()` case) + `application.yaml` using `CLOUDFLARE_AVATARS_BUCKET_NAME` / `CLOUDFLARE_AVATARS_BUCKET_URL`, and stub values in `src/test/resources/application.yaml`. Key layout: `avatars/{userId}/{uuid}.webp`. **USER ACTION STILL NEEDED: create the R2 bucket + add those two env vars to `.env` before booting the app** (already flagged in Fable-decided defaults).
  - **Test infra fix (needed by this batch's migration #1):** the new `profiles.app_language` RESTRICT FK → `app_language_options(id)` broke every repository IT (they insert `profiles` rows; the schema-only dump has no lookup rows, so default `'en'` failed the FK). Added `src/test/resources/db/seed.sql` (seeds `app_language_options` `en`/`ro`) mounted as `/docker-entrypoint-initdb.d/02-seed.sql` in `AbstractPostgresIT` (loads after `01-schema.sql`). Did NOT edit the generated `schema.sql`.

  **Files created:** `profile/internal/ProfileDtoMapper.java`, `profile/internal/entity/LanguageOptionEntity.java`, `profile/internal/repository/LanguageOptionRepository.java`, `profile/dto/LanguageOptionDto.java`, `profile/dto/LanguageUpdateRequest.java`, `profile/dto/ProfileEditRequest.java`, `profile/dto/AvatarUpdateRequest.java`; tests `ProfileDtoMapperTest`, `StorageControllerAvatarWebTest`, `src/test/resources/db/seed.sql`.
  **Files modified (main):** `profile/ProfileService.java`, `profile/internal/ProfileServiceImpl.java`, `profile/internal/controller/ProfileController.java`, `profile/internal/entity/ProfileEntity.java` (appLanguage col + bio 500 + removed DTO methods), `profile/dto/ProfileDto.java` (+`appLanguage`), `storage/StorageBucket.java`, `storage/StorageService.java`, `storage/internal/StorageServiceImpl.java`, `storage/internal/StorageController.java`, `storage/internal/cloudflare/R2Config.java`, `src/main/resources/application.yaml`.
  **Files modified (test):** `ProfileServiceImplTest` (constructor + 11 new tests), `ProfileControllerWebTest` (sampleProfile + 10 new tests), `src/test/resources/application.yaml`, `testsupport/AbstractPostgresIT.java`.
  **Test counts:** +28 new (ProfileDtoMapperTest 5, ProfileServiceImplTest 41→52, ProfileControllerWebTest 27→37, StorageControllerAvatarWebTest 2). Full suite 292/292 green.
