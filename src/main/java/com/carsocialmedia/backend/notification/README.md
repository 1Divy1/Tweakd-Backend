# notification module

Generic **in-app** notifications (no push). Owns the polymorphic `notifications` table. A
dependency-free leaf: other modules (via the `admin` orchestrator) write notifications through
`NotificationService.push(...)`; the app reads them through this module's own REST endpoints.

Current producers: feedback status changes (`feedback_status`), staff ticket replies
(`ticket_reply`), moderation warnings (`moderation_warning`), content removals (`content_removed`).
The `type` string is the client's rendering/deep-link discriminator; `payload` (jsonb) carries the
type-specific ids (e.g. `feedbackId`, `ticketId`, `caseId`).

### Social producers (posts / forums engagement)

The `posts` and `forums` modules publish small Spring domain events (in their public API) at the
point an action is known to have happened; this module's internal listeners
(`PostsNotificationListener`, `ForumsNotificationListener`) consume them **asynchronously, after the
producing transaction commits** (`@Async @Transactional(REQUIRES_NEW) @TransactionalEventListener` —
the std-Spring equivalent of Modulith's `@ApplicationModuleListener`, which isn't on this project's
classpath; `NotificationAsyncConfig` enables async). So a rolled-back like/comment never notifies,
and no module depends back on `notification` (no Modulith cycle).

Each listener: (1) reads the **recipient's** `notification_preferences` via
`ProfileService.getNotificationPreferencesOrDefault` (read-only, missing row ⇒ all-enabled default,
never throws) and drops the notification if the relevant toggle is off; (2) resolves the actor's
username via `ProfileService.findByIds` (one batch call); (3) calls `push`. The self-notify skip and
the anonymized-author skip happen upstream at the **publish site**, so any event that reaches a
listener already has a real, non-actor recipient. No aggregation in v1: each event is one row (a
like→unlike→re-like makes a second row — accepted).

| `type` | Fired when | Recipient | Pref gate | `body` | `payload` keys (all snake_case, ids as strings) |
|---|---|---|---|---|---|
| `post_like` | someone likes your post | post author | `likes_enabled` | `null` | `actor_id`, `actor_username`, `post_id` |
| `post_comment` | someone comments/replies on your post (any nesting) | post author | `comments_enabled` | comment excerpt (≤80 chars) or `null` | `actor_id`, `actor_username`, `post_id`, `comment_id` |
| `post_share` | someone shares (plain or quote) your post | post author | `shares_enabled` | `null` | `actor_id`, `actor_username`, `post_id` |
| `forum_thread_reply` | someone replies at the root of your thread | thread author | `comments_enabled` | reply excerpt (≤80 chars) or `null` | `actor_id`, `actor_username`, `thread_id`, `reply_id` |
| `forum_reply_reply` | someone replies to your reply | parent reply author only | `comments_enabled` | reply excerpt (≤80 chars) or `null` | `actor_id`, `actor_username`, `thread_id`, `parent_reply_id`, `reply_id` |
| `forum_thread_like` | someone likes your thread | thread author | `likes_enabled` | `null` | `actor_id`, `actor_username`, `thread_id` |
| `forum_reply_like` | someone likes your reply | reply author | `likes_enabled` | `null` | `actor_id`, `actor_username`, `thread_id`, `reply_id` |
| `forum_thread_tag` | you (or your car) get newly tagged in a thread | the tagged user | `tags_enabled` | `null` | `actor_id`, `actor_username`, `thread_id`, `car_tagged` (boolean) |
| `forum_reply_tag` | you (or your car) get newly tagged in a thread reply | the tagged user | `tags_enabled` | `null` | `actor_id`, `actor_username`, `thread_id`, `reply_id`, `car_tagged` (boolean) |

The two tag types fire on create **and** edit, but only for tags that are new (re-saving an
unchanged tag set is silent). A tagged car's owner is always tagged as a person too, so one
notification covers both; `car_tagged` picks the title ("… tagged your car in a thread" vs
"… tagged you in a thread"). Posts tagging still sends no notification.

`title` is a short English string embedding the actor username (e.g. `"marius_dev liked your post"`;
falls back to `"Someone …"` if the actor profile can't be resolved). Payload keys are literal
snake_case strings — the global SNAKE_CASE wire strategy renames POJO fields, not JSON-map keys, so
they're spelled out in the listener.

## Public API — `NotificationService`

| Method | Description |
|---|---|
| `push(userId, type, title, body, payload)` | Store one notification (the single write entry point for other modules) |
| `pushToAll(userIds, type, title, body, payload)` | Same notification for many users, de-duplicated; empty = no-op |
| `listNotifications(userId, cursor, size)` | One keyset page, newest first |
| `countUnread(userId)` | Unread count for the badge |
| `markRead(userId, notificationId)` | Mark one read (404 if not the caller's) |
| `markAllRead(userId)` | Mark everything read; returns affected count |

## REST endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/notifications?cursor=&size=` | The caller's feed, newest first (keyset) |
| GET | `/api/v1/notifications/unread-count` | `{ "unread": n }` |
| POST | `/api/v1/notifications/{id}/read` | Mark one read |
| POST | `/api/v1/notifications/read-all` | Mark all read → `{ "marked": n }` |

## Entities

| Entity → table | Notes |
|---|---|
| `NotificationEntity` → `notifications` | app-generated UUID id; `payload` mapped as `Map<String,Object>` via `@JdbcTypeCode(SqlTypes.JSON)`; `created_at` DB-managed; partial index on unread rows |
