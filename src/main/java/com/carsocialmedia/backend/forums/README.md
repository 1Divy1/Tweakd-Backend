# forums module

Owns the **forums feature**: topic-and-car-scoped discussion threads with Reddit-style nested
replies, likes, and user-saved filters ("shortcuts"). It owns writes (threads, replies, likes,
deletes, shortcuts) as well as reads, and composes `profile` (author display) and `garage`
(brand/model display) for cross-module data.

## Information architecture

Threads form **one pool** reached through two lenses that converge on the same intersection:

- **By car:** brand → model (`brand_id`, then `model_id`).
- **By topic:** via the `forum_thread_topics` junction (`topic_id`).

Both combine (e.g. `model = M4 AND topic = tuning`). Every list supports three **sorts**:

| Sort | Order by | Cursor key |
|---|---|---|
| `hot` (default) | `ranking_score DESC, id DESC` | `RankCursor` (score bits + id) |
| `new` | `created_at DESC, id DESC` | `TimeCursor` (ts + id) |
| `active` | `last_activity_at DESC, id DESC` | `TimeCursor` (ts + id) |

All lists keyset-paginate (`CursorPage<T>`); the cursor is opaque and the client pairs it with the
same `?sort=` it was issued for. An unrecognized `?sort=` value is a 400 (`InvalidSortException`)
so client typos fail loudly instead of silently returning the hot ordering.

**Author-deleted threads are anonymized, not hidden**: `is_deleted = true` only nulls the author in
every DTO (`deleted: true` on the wire). The thread stays listed in every feed/hub, readable,
likeable, and repliable, with title/body/replies intact. Only a thread with **zero replies** is
hard-deleted on delete.

A **shortcut** is a saved filter (any combination of brand/model/topic) pinned to the user's
landing screen, drag-reorderable, with an optional `notify` flag — owner-scoped to the JWT subject.

## Trigger-maintained columns (never written by the app)

The DB owns these; they are mapped read-only in JPA (`insertable=false, updatable=false`) and the
service re-fetches after an insert to pick them up:

- `forum_threads.ranking_score` — `trg_forum_threads_ranking_score` (BEFORE INSERT/UPDATE), a
  Reddit-style hot score.
- `forum_threads.brand_id` — `trg_forum_threads_set_brand` (BEFORE INSERT/UPDATE) derives it from
  `model_id`. **Exception:** it is mapped `insertable=true, updatable=false`, because a *brand-level*
  thread (no model) has no model to derive from, so the app sets `brand_id` directly on insert. For a
  model-scoped thread the app sets `model_id` and leaves `brand_id` null; the trigger fills it. The
  app never rewrites it after creation.
- `forum_threads.likes_count` / `reply_count` / `last_activity_at` — maintained by
  `trg_forum_posts_counts` and `trg_forum_thread_likes_count`.
- `forum_posts.likes_count` / `reply_count` — maintained by `trg_forum_posts_counts` and
  `trg_forum_post_likes_count`. The post-count trigger fires on INSERT/DELETE only, so a
  *soft-deleted* reply that still anchors children keeps counting (intentional). To honor the rule
  "a deleted, childless reply must not count", `deletePost` collapses upward: after a hard delete,
  any ancestor that is itself soft-deleted and now has no children is hard-deleted too
  (`collapseDeletedAncestors`), so a `[deleted]` placeholder lives exactly as long as it anchors
  visible children.

## Authorization (service layer — RLS is bypassed)

The backend connects with a `BYPASSRLS` service role, so **all** authorization lives in
`ForumsServiceImpl`:

- **Delete thread**: author-only. With replies → **anonymize** (`is_deleted = true`; author hidden,
  everything else stays visible and repliable); with no replies → **hard delete** (DB cascades
  topics/likes).
- **Delete reply**: author-only. With children → soft delete ("[deleted]" placeholder, subtree
  survives); childless → hard delete, then `collapseDeletedAncestors` prunes any soft-deleted
  ancestors left childless.
- **Edit** (`PATCH`): author-only, content-only (thread titles are immutable, Reddit-style).
  Rejected on a deleted thread (`ThreadDeletedException`, 409), a deleted reply
  (`ForumPostDeletedException`, 409), or a locked thread (`ThreadLockedException`, 409).
- **Reply / edit** on a locked thread → rejected (`ThreadLockedException`, 409). Replying to or
  liking a **soft-deleted reply** → rejected (`ForumPostDeletedException`, 409).
- **Likes** are idempotent *and race-safe*: inserts go through a native
  `INSERT … ON CONFLICT DO NOTHING`, so concurrent double-taps can't blow up on the composite PK;
  re-unlike is a no-op delete.
- **Shortcuts** are always constrained to `user_id = current user`; create enforces the
  "at least one of brand/model/topic" CHECK before insert; update rejects a blank name.

> **Not yet implemented:** the mod-only `is_locked` / `is_pinned` toggles (section 5 of the spec).
> They are outside the section-6 endpoint list and section-8 first-tasks, so the module *honors*
> both flags (rejects replies on locked, surfaces `pinned`/`locked` in DTOs) but exposes no endpoint
> to change them yet. Wiring them up needs the `ROLE_ADMIN` gate from `shared/security`.

## Public API — `ForumsService`

Topics, feed/hubs (`CursorPage<ThreadCardDto>`), thread detail + replies, thread/reply writes,
likes, deletes, and shortcuts CRUD. See the interface Javadoc.

### DTOs (`forums.dto`)

`TopicDto`, `TopicGroupDto`, `ThreadCardDto`, `ThreadDetailDto`, `ReplyDto` (flat — children are
fetched on demand per level), `ShortcutDto`, `CursorPage<T>`. Authors reuse
`profile.dto.ProfileSearchResultDto`; brand/model reuse `garage.dto.CarBrandDto` / `CarModelDto`.
`notify` is exposed on the wire via `@JsonProperty` (the record component is `notifyEnabled`, since
`notify` collides with `Object.notify()`).

### Reply loading model

The reply tree loads **level by level**: `GET /threads/{id}/replies` pages the top-level replies
only (no subtrees); when the user expands a reply, the client calls `GET /posts/{id}/replies` for
one keyset page of its direct children, recursively for deeper levels. `ReplyDto.replyCount`
(direct children) tells the client whether there is anything to expand.

## REST endpoints (`/api/v1/forums`)

| Method | Path | Notes |
|---|---|---|
| GET | `/topics` | grouped by kind (`component`/`format`) |
| GET | `/feed?sort=&cursor=&size=` | global |
| GET | `/brands/{brandId}/threads?sort=&cursor=` | brand hub |
| GET | `/models/{modelId}/threads?sort=&topic=&cursor=` | model hub |
| GET | `/topics/{topicId}/threads?sort=&brand=&model=&cursor=` | topic hub |
| GET | `/threads/{id}` | detail (incl. `viewerHasLiked`, `deleted`) |
| GET | `/threads/{id}/replies?cursor=` | keyset page of root replies (no subtrees) |
| GET | `/posts/{id}/replies?cursor=` | keyset page of a reply's direct children |
| POST | `/threads` | create (201) |
| PATCH | `/threads/{id}` | author edits OP body (content only) |
| POST | `/threads/{id}/replies` | reply (201) |
| PATCH | `/posts/{id}` | author edits reply text |
| POST / DELETE | `/threads/{id}/like` | idempotent + race-safe (204) |
| POST / DELETE | `/posts/{id}/like` | idempotent + race-safe (204); 409 on deleted reply |
| DELETE | `/threads/{id}` | author-only: anonymize, or hard-delete if replyless (204) |
| DELETE | `/posts/{id}` | author-only, soft/hard (204) |
| GET / POST | `/shortcuts` | list / create |
| PATCH | `/shortcuts/reorder` | full-set reorder (declared before `/{id}`) |
| PATCH / DELETE | `/shortcuts/{id}` | update (name/notify) / delete |

## Entities → tables

`ForumTopicEntity`→`forum_topics` (text PK slug), `ForumThreadEntity`→`forum_threads`,
`ForumPostEntity`→`forum_posts` (self-ref `parent_post_id`),
`ForumThreadTopicEntity`→`forum_thread_topics` (`@EmbeddedId` thread_id+topic_id),
`ForumThreadLikeEntity`/`ForumPostLikeEntity` (composite-PK likes),
`ForumShortcutEntity`→`forum_shortcuts`.

## Cross-module dependency

`forums → profile` (author `findByIds`), `forums → garage` (added
`findBrandsByIds` / `findModelsByIds` batch lookups, mirroring `findCarsByIds`), `forums → shared`.
Nothing depends back on `forums`. Verified by `ModularityTests`.
