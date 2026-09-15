# notification module

Generic in-app notifications **plus push delivery**. Owns the polymorphic `notifications` table. A
dependency-free leaf: other modules (via the `admin` orchestrator) write notifications through
`NotificationService.push(...)`; the app reads them through this module's own REST endpoints.

Current producers: feedback status changes (`feedback_status`), staff ticket replies
(`ticket_reply`), moderation warnings (`moderation_warning`), content removals (`content_removed`).
The `type` string is the client's rendering/deep-link discriminator; `payload` (jsonb) carries the
type-specific ids. **Every payload key is snake_case and every id is a string**, across all
producers — the global SNAKE_CASE wire strategy renames POJO fields, not JSON-map keys, so they are
spelled out literally at each call site.

**`actor_avatar_url` is added at read time, never stored.** `GET /notifications` batch-resolves the
current avatar of every distinct `actor_id` on the page (one `ProfileService.findByIds` call) and
adds it to that row's `payload` in the response. That covers every producer recording an
`actor_id` — Spring listeners and Postgres-written rows like `dm` alike. It is deliberately not
snapshotted into the stored payload: avatar objects are deleted from R2 when replaced, so a stored URL
would go dead the moment its owner changed their photo. Actors without a photo get no key, and the
client falls back to their initial. Pushes and the `markRead` response don't carry it.

| `type` | Recipient | `payload` keys |
|---|---|---|
| `feedback_status` | author + subscribers | `feedback_id`, `status` |
| `ticket_reply` | the requester | `ticket_id` |
| `moderation_warning` | the author | `target_type`, `target_id`, `case_id` |
| `content_removed` | the author | `target_type`, `target_id`, `case_id` |

`target_type` ∈ `post` / `comment` / `forum_thread` / `forum_thread_reply`, and decides which screen
`target_id` belongs to.

> **Routing caveat for `content_removed`:** the content is deleted immediately before the
> notification is sent, so `target_id` names a row that no longer exists. It is there for context
> and labelling, **not** as a deep-link destination — routing to it lands on a 404. `target_id` on
> `moderation_warning` *is* routable: a warning leaves the content in place.

*(These four keys were `feedbackId` / `ticketId` / `caseId` / `targetType` until 2026-08-31, and
`target_id` was absent entirely — which is why the two moderation types could not be deep-linked at
all. Clients reading the old camelCase keys must be updated in the same release.)*

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
| `post_like` | someone likes your post — once per liker, ever (unlike + like again stays silent) | post author | `likes_enabled` | `null` | `actor_id`, `actor_username`, `post_id` |
| `post_comment` | someone comments/replies on your post (any nesting) | post author | `comments_enabled` | comment excerpt (≤80 chars) or `null` | `actor_id`, `actor_username`, `post_id`, `comment_id` |
| `post_share` | someone reposts your post — once per reposter, ever (undo + repost again stays silent) | post author | `shares_enabled` | `null` | `actor_id`, `actor_username`, `post_id` |
| `forum_thread_reply` | someone replies at the root of your thread | thread author | `comments_enabled` | reply excerpt (≤80 chars) or `null` | `actor_id`, `actor_username`, `thread_id`, `reply_id` |
| `forum_reply_reply` | someone replies to your reply | parent reply author only | `comments_enabled` | reply excerpt (≤80 chars) or `null` | `actor_id`, `actor_username`, `thread_id`, `parent_reply_id`, `reply_id` |
| `forum_thread_like` | someone likes your thread — once per liker, ever | thread author | `likes_enabled` | `null` | `actor_id`, `actor_username`, `thread_id` |
| `forum_reply_like` | someone likes your reply — once per liker, ever | reply author | `likes_enabled` | `null` | `actor_id`, `actor_username`, `thread_id`, `reply_id` |
| `forum_thread_tag` | you (or your car) get newly tagged in a thread | the tagged user | `tags_enabled` | `null` | `actor_id`, `actor_username`, `thread_id`, `car_tagged` (boolean) |
| `forum_reply_tag` | you (or your car) get newly tagged in a thread reply | the tagged user | `tags_enabled` | `null` | `actor_id`, `actor_username`, `thread_id`, `reply_id`, `car_tagged` (boolean) |
| `post_tag` | you (or your car) get newly tagged in a post | the tagged user | `tags_enabled` | `null` | `actor_id`, `actor_username`, `post_id`, `car_tagged` (boolean) |
| `post_comment_tag` | you (or your car) get tagged in a post comment | the tagged user | `tags_enabled` | `null` | `actor_id`, `actor_username`, `post_id`, `comment_id`, `car_tagged` (boolean) |

The four tag types fire on create **and** (where the surface is editable) on edit, but only for tags
that are new — re-saving an unchanged tag set is silent. Comments have no edit endpoint, so every
`post_comment_tag` is a create. A tagged car's owner is tagged as a person too unless the car is the
author's own, so one notification covers both; `car_tagged` picks the title ("… tagged your car in a
thread" vs "… tagged you in a thread"). A user tagged in a comment on their own post gets both a
`post_comment` and a `post_comment_tag` — different events, different meanings.

`title` is a short English string embedding the actor username (e.g. `"marius_dev liked your post"`;
falls back to `"Someone …"` if the actor profile can't be resolved). Payload keys are literal
snake_case strings — the global SNAKE_CASE wire strategy renames POJO fields, not JSON-map keys, so
they're spelled out in the listener.

### Map-event producers (events / contests)

`mapevents` publishes domain events the same way, consumed here by `MapEventsNotificationListener`
under the same after-commit async rules. The full gating table lives in
[`mapevents/README.md`](../mapevents/README.md#notifications); the payload contract is below, since
that is what the app routes on.

| `type` | `payload` keys |
|---|---|
| `map_event_approved` / `map_event_rejected` | `event_id` |
| `map_event_car_decided` | `event_id`, `car_id` |
| `map_event_car_registered` | `event_id`, `car_id`, `actor_id`, `actor_username` |
| `map_event_organizer_added` | `event_id`, `actor_id`, `actor_username` |
| `contest_entry_requested` | `event_id`, `contest_id`, `car_id`, `actor_id`, `actor_username` |
| `contest_entry_decided` | `event_id`, `contest_id`, `car_id`, `accepted` (boolean) |
| `contest_opened` | `event_id`, `contest_id` |
| `contest_results` | `event_id`, `contest_id`, `winner_car_id` |
| `contest_placed` | `event_id`, `contest_id`, `car_id`, `rank` (1–3) |
| `participant_card_ready` | `event_id` — ungated, once per owner per event, fired when an organizer marks the event finished |

Every contest type carries `event_id` **and** `contest_id`, so the app can deep-link straight to the
contest without first resolving the event. `contest_placed` is sent once per podium owner — the
recipient's own car is in `car_id`, and `rank` is what the title says ("your car finished 2nd").

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

## Push notifications (FCM)

Every notification written through this module is also delivered to the recipient's devices as a
Firebase Cloud Messaging push — push is a **second delivery channel for the rows above**, not a
separate system, which is why `data.notification_id` is the `notifications.id` the client marks read.

### Who sends what

| Types | Sender |
|---|---|
| the 21 types in the tables above | **this backend** (`internal/push`, `firebase-admin`) |
| `dm` | **a Supabase edge function**, never this backend |

DMs bypass Spring entirely on the write path: the Flutter client calls the `dm_send_message`
Postgres RPC directly and live delivery rides Supabase Realtime, so the backend never observes a
message being sent. The edge function is triggered by the `dm` row insert and calls FCM itself.
`PushDispatcher` skips `type = 'dm'` defensively — rows written by Postgres publish no Spring event,
so the two senders cannot collide.

### How a push happens

`NotificationServiceImpl.push` / `pushToAll` publish a `NotificationsCreatedEvent` carrying the new
ids. `PushDispatcher` consumes it with `@Async @Transactional(REQUIRES_NEW)
@TransactionalEventListener` — the same shape the in-app listeners use — so a rolled-back like or
comment never pushes. The dispatcher then:

1. reloads the rows and drops any `dm`;
2. batch-loads every recipient's devices (one query) and unread counts (**one grouped query**, not
   one per recipient);
3. builds one message per (recipient, device) pair and sends via `sendEach` in chunks of 500;
4. deletes tokens FCM reported as permanently dead.

Notification preferences are **not** re-checked here — the producing listeners already gate on them
before calling `push`, so a row existing means delivery was approved.

Delivery is best effort and never fails the producer: the in-app row is committed and readable over
REST regardless. There is no retry.

### Token lifecycle

`sendEach` returns per-message results in input order, which is how a failure maps back to a token:

| `MessagingErrorCode` | Action |
|---|---|
| `UNREGISTERED`, `INVALID_ARGUMENT`, `SENDER_ID_MISMATCH` | delete the row — the token is dead |
| `UNAVAILABLE`, `INTERNAL`, `QUOTA_EXCEEDED`, `THIRD_PARTY_AUTH_ERROR` | keep it — transient |

A whole-batch failure prunes nothing: nothing is provably dead.

### Payload

```json
{
  "notification": { "title": "...", "body": "..." },
  "data": {
    "type": "post_like", "notification_id": "...", "unread_count": "7",
    "actor_id": "...", "actor_username": "...", "post_id": "..."
  },
  "android": { "notification": { "channel_id": "tweakd_default" } },
  "apns": { "payload": { "aps": { "badge": 7, "sound": "default" } } }
}
```

Every `data` value is a string — FCM rejects anything else, and it matters here because `payload`
legitimately carries a Boolean (`car_tagged`). Null values are dropped rather than stringified to
`"null"`. `body` is truncated to 200 chars (FCM caps the payload at 4 KB). `unread_count` and the
APNs badge both come from `countUnread`, i.e. unread `notifications` rows.

### Configuration

`firebase.*` in `application.yaml`. Credentials come from **Application Default Credentials** — the
Cloud Run runtime service account in production, `GOOGLE_APPLICATION_CREDENTIALS` or `gcloud auth
application-default login` locally. If they cannot be resolved the app still starts and
`NoOpFcmSender` takes over, so the test suite and a fresh checkout need no Firebase setup.

`firebase-admin` excludes `google-cloud-firestore` and `google-cloud-storage`; FCM speaks plain HTTP
and those pull in the whole gRPC stack (~48 MB, 67 jars).

## Device endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/notifications/devices` | Idempotent upsert keyed on the token → 200 |
| DELETE | `/api/v1/notifications/devices` | Unregister; token in the **body** → 204 |

```json
{ "token": "...", "platform": "ios|android", "app_version": "1.0.0+1", "locale": "en" }
```

The token travels in the DELETE **body, not the path**: a path segment is written verbatim into
access, proxy and CDN logs, and an FCM token is a device-addressable secret.

Security properties, all deliberate:

- the account is always the JWT subject, never anything in the body;
- unregister is scoped `where token = ? and user_id = ?`, so one user cannot cut off another's push
  by replaying their token;
- unregister returns 204 whether or not a row matched — it is not a token-existence oracle;
- no endpoint returns a token and there is no list-devices endpoint;
- a per-user cap of 10 devices, oldest evicted first.

## Entities

| Entity → table | Notes |
|---|---|
| `NotificationEntity` → `notifications` | app-generated UUID id; `payload` mapped as `Map<String,Object>` via `@JdbcTypeCode(SqlTypes.JSON)`; `created_at` DB-managed; partial index on unread rows |
| `UserDeviceEntity` → `user_devices_firebase_token` | FCM tokens, one row per device. `token` is UNIQUE **on its own**, which is what implements device handoff: re-registering reassigns the row to the new user. RLS-enabled with no policies and revoked from `anon`/`authenticated`, so only the backend (owner) and the edge function (`service_role`, select+delete only) can read it. Shared with the `dm` edge function. |
