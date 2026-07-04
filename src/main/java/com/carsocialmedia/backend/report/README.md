# report module (Moderation)

Owns the user-reporting tables — `post_reports`, `comment_reports`, `profile_reports`,
`forum_thread_reports`, `forum_thread_reply_reports` — and the shared `report_reasons` reference
data. **Filing** a report is a resource-oriented action, so those endpoints live next to the thing
being reported and delegate the insert here:

- report a post / comment → **`posts` module** (`PostReportController`)
- report a profile → **`profile` module** (`ProfileReportController`)
- report a forum thread / reply → **`forums` module** (`ForumReportController`)

The module owns exactly **one** REST endpoint of its own: `GET /api/v1/reports/mine`
(`MyReportsController`), a user's read-only view of the reports *they* filed. Unlike filing, listing
your own reports is a pure read of the report tables — it needs no lookup into `posts` / `profile`,
so it creates no Modulith cycle and can live here (see below).

## Why the endpoints live elsewhere

Keeping `report` a **dependency-free leaf** avoids a Spring Modulith cycle. If `report` validated
that the reported post exists (by calling into `posts`) while `posts` also called into `report` to
file the report, that would be `posts → report → posts` — a forbidden cycle. So responsibilities are
split by who owns the knowledge:

- **The resource module** (`posts` / `profile` / `forums`) validates its own target exists and
  blocks self-reports (it knows the post author / comment author / profile owner / thread-or-reply
  author), then delegates.
- **The report module** owns only what the report tables know: rejecting an invalid/mismatched
  `reasonId` and rejecting a duplicate report.

Dependency direction: `posts → report`, `profile → report`, `forums → report`, `report → shared`
only.

## Public API — `ReportService`

| Method | Description |
|---|---|
| `reportPost(reporterId, postId, reasonId)` | Insert a post report (validates reason + duplicate) |
| `reportComment(reporterId, commentId, reasonId)` | Insert a comment report |
| `reportProfile(reporterId, profileId, reasonId)` | Insert a profile report |
| `reportForumThread(reporterId, threadId, reasonId)` | Insert a forum thread report |
| `reportForumReply(reporterId, replyId, reasonId)` | Insert a forum thread reply report |
| `listPostReportReasons()` / `listCommentReportReasons()` / `listProfileReportReasons()` / `listForumThreadReportReasons()` / `listForumReplyReportReasons()` | Preset reasons for the picker |
| `listMyReports(reporterId)` | Every report the reporter filed (post + comment + profile + forum thread + forum reply), newest first — for their "my reports" view |

`reporterId` and the target id are trusted to exist (the reporter is the JWT subject; the target is
validated by the calling module and by the DB foreign key). `reasonId` is **optional** (`null` =
no preset reason).

### DTOs (`report.dto`, exposed as a `@NamedInterface`)

| Type | Used for |
|---|---|
| `ReportRequest(reasonId)` | Optional request body for the report endpoints |
| `ReportReasonDto(id, reason)` | One preset reason for the report UI |
| `MyReportDto(targetType, targetId, reason, status, createdAt)` | One report in the reporter's own "my reports" feed (`targetType` = `post`/`comment`/`profile`/`forum_thread`/`forum_thread_reply`) |

### Exceptions

| Exception | HTTP | Trigger |
|---|---|---|
| `DuplicateReportException` | 409 | Reporter already reported this target |
| `InvalidReportReasonException` | 400 | `reasonId` doesn't exist or is scoped to a different target |

Self-report and target-not-found are the calling module's responsibility:
`posts` throws `CannotReportOwnContentException` (400) / `PostNotFoundException` /
`CommentNotFoundException`; `profile` throws `CannotReportSelfException` (400) /
`ProfileNotFoundException`; `forums` throws `CannotReportOwnForumContentException` (400) /
`ThreadNotFoundException` / `ForumPostNotFoundException`.

## REST endpoints

| Method | Path | Module |
|---|---|---|
| POST | `/api/v1/posts/{postId}/report` | posts |
| POST | `/api/v1/posts/{postId}/comments/{commentId}/report` | posts |
| GET | `/api/v1/posts/report-reasons` | posts |
| GET | `/api/v1/posts/comments/report-reasons` | posts |
| POST | `/api/v1/profile/{username}/report` | profile |
| GET | `/api/v1/profile/report-reasons` | profile |
| POST | `/api/v1/forums/threads/{threadId}/report` | forums |
| POST | `/api/v1/forums/replies/{replyId}/report` | forums |
| GET | `/api/v1/forums/threads/report-reasons` | forums |
| GET | `/api/v1/forums/replies/report-reasons` | forums |
| GET | `/api/v1/reports/mine` | report |

The report POSTs take an optional `{ "reasonId": "…" }` body and return `204 No Content`.

`GET /api/v1/reports/mine` returns the authenticated user's own reports — post, comment, and profile
reports merged into a single list, newest first — as `List<MyReportDto>`. It is always scoped to the
JWT subject, so a user only ever sees their own reports, never another user's.

## Entities

| Entity → table | Notes |
|---|---|
| `PostReportEntity` → `post_reports` | Composite PK `(post_id, reporter_id)`; `reason_id` nullable |
| `CommentReportEntity` → `comment_reports` | Composite PK `(comment_id, reporter_id)` |
| `ProfileReportEntity` → `profile_reports` | Composite PK `(profile_id, reporter_id)` |
| `ForumThreadReportEntity` → `forum_thread_reports` | Composite PK `(thread_id, reporter_id)` |
| `ForumThreadReplyReportEntity` → `forum_thread_reply_reports` | Composite PK `(reply_id, reporter_id)` |
| `ReportReasonEntity` → `report_reasons` | Seeded reference data, scoped per `target` (`ReportTarget`: `post`/`comment`/`profile`/`forum_thread`/`forum_thread_reply`) |

`status` (native PG enum `report_status`, defaults `pending`) and `created_at` are DB-managed
(`insertable = false`); moderators update `status` afterwards. All five report tables are
`ON DELETE CASCADE` on their target and reporter, so deleting a post/comment/profile/thread/reply
cleans up its reports.
