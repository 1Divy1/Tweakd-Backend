# notification module

Generic **in-app** notifications (no push). Owns the polymorphic `notifications` table. A
dependency-free leaf: other modules (via the `admin` orchestrator) write notifications through
`NotificationService.push(...)`; the app reads them through this module's own REST endpoints.

Current producers: feedback status changes (`feedback_status`), staff ticket replies
(`ticket_reply`), moderation warnings (`moderation_warning`), content removals (`content_removed`).
The `type` string is the client's rendering/deep-link discriminator; `payload` (jsonb) carries the
type-specific ids (e.g. `feedbackId`, `ticketId`, `caseId`).

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
