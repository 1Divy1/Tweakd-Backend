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
| 5 | Notification producers (posts/forums events → notification listeners) | DONE | Opus agent C |
| 6 | Fable code review + security review of the whole diff | DONE | Fable |
| 7 | Mobile API templates handed to user | DONE | Fable |

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
- Checkpoint progress into this file's Work log INCREMENTALLY (one terse line per milestone), then
  do the full status-table update when done. No commits. (Owner rule 2026-07-17: subagent context
  dies with the subagent — the tracker must be current enough to resume from at any moment.)

## Work log

- 2026-07-31 (profile "tags" section — a third tab beside posts & garage): DONE. Full suite
  **421 tests**, 0 failures, Testcontainers ITs included; `ModularityTests` green. **No migration
  needed** — every reverse-lookup index the queries use already existed.

  **New module `tags`** (`/api/v1/tags`), modelled on `feed`: owns no data, composes `posts`
  (post + comment tags), `forums` (thread + reply tags), `garage` (the owner's car ids) and
  `profile` (username → id). DMs excluded per the owner's ask.

  | Method | Path | Notes |
  |---|---|---|
  | GET | `/by-username/{username}?cursor=&size=` | A profile's tags section, merged + keyset-paged |
  | GET | `/me?cursor=&size=` | The caller's own |
  | DELETE | `/{kind}/{targetId}` | Untag yourself; 204, 400 unknown kind, 404 content gone |

  **Owner decisions (asked before building, 2026-07-31):** one merged feed (not sub-tabs) with a
  `kind` discriminator (`post` / `post_comment` / `forum_thread` / `forum_reply`); content the
  profile owner authored is **excluded**; items reuse the existing DTOs (`PostDto`, `CommentDto` +
  parent `PostDto`, `ThreadCardDto`, `ReplyDto` + parent `ThreadCardDto`); untag **deletes** the tag
  (not a hidden flag) and is hidden from every viewer.

  **Untag semantics:** deleting the caller's person tag also deletes the tags of the caller's *own*
  cars on that content — otherwise the removal would leave a tagged car whose owner is untagged,
  violating the invariant the tagging endpoints enforce. Other people's tags are untouched; the
  author may re-tag. Idempotent.

  **Feed mechanics:** ordered by *when the tag was made* (`coalesce(tag.created_at,
  content.created_at)` — `tagged_people` / `tagged_cars` have a nullable `created_at`), `id` as
  tiebreaker. One opaque cursor is re-applied by all four streams; each returns ≤ `size + 1` refs,
  which are merged, deduped (person + own-car tag on the same content = one item), sorted, trimmed,
  and only then hydrated (one batch call per content type; a comment's parent post rides the post
  batch, a reply's parent thread the thread batch). Soft-deleted comments/replies are skipped;
  anonymized threads are kept (content intact).

  **Files created (main):** `shared/tagging/TaggedContentRef.java` (+ the Postgres-compatible
  unsigned-uuid `NEWEST_FIRST` comparator); `tags/` — `package-info.java`, `TagsService.java`,
  `README.md`, `dto/{package-info,TaggedContentKind,TaggedItemDto,TaggedItemPageDto}.java`,
  `exception/{InvalidTagCursorException,UnknownTaggedContentKindException}.java`,
  `internal/{TagsController,TagsServiceImpl,TagCursor}.java`;
  `posts/internal/repositories/TagRefRow.java`, `forums/internal/repositories/TagRefRow.java`,
  `forums/dto/package-info.java` (`@NamedInterface("dto")` — required for `tags` to consume
  `ThreadCardDto` / `ReplyDto`; `posts/dto` already had one).
  **Files modified (main):** `PostsService` + `PostsServiceImpl` (findTaggedPostRefs /
  findTaggedCommentRefs / getPostsByIds / getCommentsByIds / removeSelfTagsFromPost /
  removeSelfTagsFromComment; `toCommentDtos` extracted from `toCommentPage`), `ForumsService` +
  `ForumsServiceImpl` (findTaggedThreadRefs / findTaggedReplyRefs / getThreadCardsByIds /
  getRepliesByIds / removeSelfTagsFromThread / removeSelfTagsFromReply; `toReplyDtos` now takes a
  thread→author map so the "Author" badge survives a mixed-thread batch), `GarageService` +
  `GarageServiceImpl` + `CarRepository` (`findCarIdsByOwner` / `findIdsByOwnerId`), the eight tag
  repositories (keyset ref query + targeted delete each), `CONTEXT.md`, `posts/README.md`,
  `forums/README.md`, `garage/README.md`.
  **Files created (test):** `tags/internal/TagsServiceImplTest.java` (14),
  `tags/internal/TagsControllerWebTest.java` (7),
  `posts/internal/repositories/PostTagRefRepositoryIT.java` (10),
  `forums/internal/repositories/ForumTagRefRepositoryIT.java` (8).
  **Files modified (test):** `PostsTaggingTest` (+5 untag), `ForumsTaggingTest` (+4 untag).
  **Test counts:** +48 net (373 → 421).

  **Deviations / decisions to flag to user:**
  - **Path is `/api/v1/tags/...`, not `/api/v1/profiles/{username}/tags`** (which the untag preview
    sketched during the Q&A). The project rule is `/api/v1/<module>`, and `feed` sets the precedent
    for a composing module owning its own base path. `by-username/{username}` mirrors
    `GET /api/v1/posts/by-username/{username}`.
  - **A page can be shorter than `size` even when more items exist** — dedup happens after the
    fetch. `next_cursor == null` is the only end-of-feed signal; documented in `TaggedItemPageDto`.
  - **Anonymized (author-deleted) forum threads stay in the feed**; soft-deleted comments and
    replies are dropped. An anonymized thread keeps its title/body/replies, a deleted
    comment/reply has no content and no tags to show.
  - **Per-item viewer state is the viewer's**, not the profile owner's (`viewer_has_liked` etc.),
    since the DTOs are hydrated with the caller's id.
  - Ordering compares uuids **unsigned** (`TaggedContentRef.NEWEST_FIRST`) to match Postgres's
    byte-wise `uuid` ordering; `UUID.compareTo` is signed and would disagree with the SQL keyset on
    equal-timestamp ties, which could skip or repeat a row across pages.
  - `forums/dto` gained `@NamedInterface("dto")`. This only *widens* what forums exposes (its root
    package stays the unnamed interface); no existing consumer changed.

- 2026-07-31 (comment tagging — tag people & cars in a post's comment section): DONE (code),
  **migration NOT yet applied to Supabase**.

  **Endpoint change** (`PostController`, base `/api/v1/posts`): `POST /{postId}/comments` accepts two
  new optional body fields `tagged_people: [uuid]` / `tagged_cars: [uuid]` (≤30 each, deduplicated
  server-side). Same rule as posts/threads/replies: a car may only be tagged when its owner is in
  `tagged_people` or is the comment's author → else 400 `CarOwnerNotTaggedException`; unknown
  person/car id → 400 `InvalidReferenceException`. `CommentDto` now carries
  `tagged_people: [{id, username, avatar_url}]` and `tagged_cars: [CarSummaryDto]` on **all three**
  read paths (create response, `GET /{postId}/comments`, `GET /{postId}/comments/{commentId}/replies`).
  A soft-deleted comment returns empty tag lists (join rows are kept, just not looked up — same
  treatment as forum replies).

  **USER ACTION NEEDED: apply `migrations/2026-07-31_comment_tagging.sql` to Supabase** (two tables
  `comment_tagged_people` / `comment_tagged_cars`, both FK-CASCADE off `comments`, `profiles`,
  `cars`, RLS enabled + select policy). Until it runs, the app will fail `ddl-auto: validate` at
  startup. The test dump `src/test/resources/db/schema.sql` was hand-edited to match; re-run
  `./scripts/dump-schema.sh` after applying to re-sync it. Both the migration and the hand-edited
  dump statements were executed against a throwaway local Postgres to verify they parse and apply.

  **Notifications**: new type `post_comment_tag` (gated by `tags_enabled`, recipient = the tagged
  user, payload `actor_id`, `actor_username`, `post_id`, `comment_id`, `car_tagged`). Per the owner's
  2026-07-31 decision the pre-existing gap for **post-level** tags was closed in the same pass: new
  type `post_tag` (payload `actor_id`, `actor_username`, `post_id`, `car_tagged`), fired from
  `createPost` / `updatePost` for **newly added** tags only. `notifications.type` is plain text, so
  neither type needed a migration.

  **Files created (main):** `posts/PostTaggedEvent.java`, `posts/PostCommentTaggedEvent.java`,
  `posts/internal/entities/CommentTaggedPersonEntity.java` + `CommentTaggedPersonId.java`,
  `CommentTaggedCarEntity.java` + `CommentTaggedCarId.java`,
  `posts/internal/repositories/CommentTaggedPersonRepository.java` + `CommentTaggedCarRepository.java`;
  `migrations/2026-07-31_comment_tagging.sql`.
  **Files modified (main):** `posts/dto/CommentDto.java` (+`taggedPeople`/`taggedCars` after
  `content`), `posts/dto/request/CreateCommentRequest.java` (+ the two `@Size(max = 30)` lists),
  `posts/internal/PostsServiceImpl.java` (two repos injected; `addComment` validates/persists/echoes
  tags; `toCommentPage` batch-resolves them; `validateTaggedCars` now returns `ownerByCar`; new
  `publishTagEvents` / `TagRecipient` / `added` / `resolve` helpers mirroring `ForumsServiceImpl`),
  `notification/internal/PostsNotificationListener.java` (+2 handlers), `posts/README.md`,
  `notification/README.md`, `src/test/resources/db/schema.sql`.
  **Files created (test):** `posts/internal/PostsTaggingTest.java` (14).
  **Files modified (test):** `notification/internal/PostsNotificationListenerTest.java` (7→14),
  `posts/internal/PostsNotificationPublishingTest.java` (constructor + 4-arg `CreateCommentRequest`).

  **Verification:** `./mvnw clean test` → **373 tests, 0 failures, 0 errors** (baseline 358; +15
  net), Testcontainers ITs included — so the hand-edited `schema.sql` loads into a real Postgres.
  `ModularityTests#verifiesModularStructure` GREEN (no new module dependency: `notification` already
  depended on `posts`). Not yet exercised against the live Supabase DB or a running app.

  **Deviations / decisions to flag:**
  - No mobile-breaking change: untagged comments serialize exactly as before, and the two request
    fields are optional.
  - `CommentDto` gained its two fields **after `content`** (mirrors `ReplyDto`); it's an additive
    JSON change, but any positional constructor call had to be updated.
  - Comment tags are create-only — there is no `PATCH /comments/{id}` in this module, so no
    replace-all/edit semantics were built (unlike threads/replies, which have one).
  - A user tagged in a comment on their own post gets both `post_comment` and `post_comment_tag`.
  - Table names are `comment_tagged_people` / `comment_tagged_cars` (prefixed), not the unprefixed
    `tagged_people` / `tagged_cars` the posts tables use.

- 2026-07-17 (Item 5 — notification producers): DONE. Full suite **343 tests**, 0 failures
  (baseline 308; +35 net). `ModularityTests#verifiesModularStructure` GREEN — new dependencies
  notification→posts and notification→forums created no cycle (neither depends back on notification).

  **The 7 in-app notification types** (each event = one notification row; no aggregation in v1 —
  like→unlike→re-like makes a second row, accepted). Recipient's `notification_preferences` gate the
  push; a missing prefs row (or missing profile) reads as all-enabled. Self-notify + anonymized-author
  skips happen at the PUBLISH site. `title` embeds the actor username (fallback `"Someone …"`).
  Payload keys are literal snake_case; all ids serialized as strings:

  | type | body | payload JSON shape |
  |---|---|---|
  | `post_like` (`likes_enabled`) | null | `{"actor_id","actor_username","post_id"}` |
  | `post_comment` (`comments_enabled`) | comment excerpt ≤80 or null | `{"actor_id","actor_username","post_id","comment_id"}` |
  | `post_share` (`shares_enabled`) | null | `{"actor_id","actor_username","post_id"}` |
  | `forum_thread_reply` (`comments_enabled`) | reply excerpt ≤80 or null | `{"actor_id","actor_username","thread_id","reply_id"}` |
  | `forum_reply_reply` (`comments_enabled`) | reply excerpt ≤80 or null | `{"actor_id","actor_username","thread_id","parent_reply_id","reply_id"}` |
  | `forum_thread_like` (`likes_enabled`) | null | `{"actor_id","actor_username","thread_id"}` |
  | `forum_reply_like` (`likes_enabled`) | null | `{"actor_id","actor_username","thread_id","reply_id"}` |

  **Files created (main):** events in module public API — `posts/PostLikedEvent.java`,
  `PostCommentedEvent.java`, `PostSharedEvent.java`; `forums/ForumThreadRepliedEvent.java`,
  `ForumReplyRepliedEvent.java`, `ForumThreadLikedEvent.java`, `ForumReplyLikedEvent.java`. Listeners
  `notification/internal/PostsNotificationListener.java`, `ForumsNotificationListener.java`;
  `notification/internal/NotificationAsyncConfig.java`.
  **Files modified (main):** `profile/ProfileService.java` + `profile/internal/ProfileServiceImpl.java`
  (new read-only `getNotificationPreferencesOrDefault(UUID)`); `posts/internal/PostsServiceImpl.java`
  (inject `ApplicationEventPublisher`; `likePost`/`sharePost` load the post row for its author and
  publish on real insert; `addComment` publishes for the post author at any nesting; `publishSocialEvent`
  + `excerpt` helpers); `forums/internal/ForumsServiceImpl.java` (same injection; `addReply` root→thread
  author / nested→parent-reply author only; `likeThread`/`likePost` publish only when insert count == 1);
  `forums/internal/repositories/ForumThreadLikeRepository.java` + `ForumPostLikeRepository.java`
  (`insertIgnoringConflict` return type `void`→`int` so a real insert is observable);
  `notification/README.md`, `posts/README.md`, `forums/README.md`.
  **Files created (test):** `notification/internal/PostsNotificationListenerTest` (8),
  `ForumsNotificationListenerTest` (8), `posts/internal/PostsNotificationPublishingTest` (7),
  `forums/internal/ForumsNotificationPublishingTest` (10).
  **Files modified (test):** `profile/internal/ProfileServiceImplTest` (+3 for the new prefs method,
  52→55). New tests: 36.

  **Deviations / decisions to flag to user:**
  - **`@ApplicationModuleListener` is NOT on this project's classpath** (only `spring-modulith-starter-core`
    is present; the annotation lives in the events module). Adding the events module risks auto-activating
    the persistent event-publication registry, which would need an `event_publication` table and could
    crash startup under `ddl-auto: validate`. So the listeners use its exact standard-Spring composition
    `@Async @Transactional(propagation = REQUIRES_NEW) @TransactionalEventListener` — same async +
    after-commit + isolated-tx semantics — and `NotificationAsyncConfig` adds `@EnableAsync` (async was
    not enabled anywhere before; the existing `DmPresencePusher` uses a synchronous `@EventListener`).
  - **Self-notify + null/anonymized-author skips are done at the PUBLISH site** (one consistent place,
    via `publishSocialEvent`), not in the listener. Listeners still gate on prefs and resolve the username.
  - **Forum like repos now return `int`** from `insertIgnoringConflict` (was `void`) so "notify only on a
    real like" is observable; callers previously ignored the return, so this is source-compatible.
  - **Posts producers now load the post row** (`likePost`/`sharePost`/`addComment`) instead of a bare
    `existsById` check, to get the author id for the recipient. Same 404 behavior on a missing post.
  - Excerpt truncation is ~80 chars with a trailing `…`; blank comment/reply text ⇒ null body.
  - Full-suite count came out 343 (not the 308+36=344 I'd expect) — a one-test discrepancy vs the stated
    308 baseline, most likely the committed-tree baseline differing by one; 0 failures either way.
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
