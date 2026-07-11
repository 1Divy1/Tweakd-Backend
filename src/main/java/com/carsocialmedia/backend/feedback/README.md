# feedback module

Owns user-submitted app feedback (bug reports, feature requests, general notes), the **public
feedback board** (votes, comments, subscriptions), and the admin-dashboard operations over it
(official responses, status lifecycle). Tables: `feedback`, `feedback_votes`, `feedback_comments`,
`feedback_subscriptions`, plus the reference tables `feedback_type_options`,
`feedback_feature_options`, `feedback_status_options`.

Depends on `profile` (author cards on board items/comments) and `shared`. It deliberately does
**not** depend on `notification`: `updateStatus` returns who should be notified and the `admin`
module does the fan-out.

**Status lifecycle:** `submitted → under_review → investigating → planned → in_development →
testing → released`, plus `fixed` (bugs) and `declined`. Statuses are seeded rows with name +
color, in roadmap order.

## Public API — `FeedbackService`

User side: `submitFeedback`, `listFeedbackTypes`, `listFeedbackFeatures`, `listMyFeedback`,
`listFeedbackStatuses`.

Board: `getBoard(viewerId, sort, type, status, cursor, size)` (sorts: `top` = most votes, `new`,
`trending` = most votes in 7 days), `getBoardItem`, `vote`/`unvote` (idempotent, ON CONFLICT),
`getComments`/`addComment`/`deleteComment` (own comments only), `subscribe`/`unsubscribe`.

Admin (no auth here — the `admin` module gates it): `listAllFeedback` (same sorts/filters, admin
fields), `getStats` (total, new this week, by type, by status), `respond(feedbackId, response)`,
`updateStatus(feedbackId, statusId)` → `FeedbackStatusChangeDto` with `recipients` = author +
subscribers, de-duplicated.

## REST endpoints

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/feedback` | Submit feedback → `204` |
| GET | `/api/v1/feedback/types` / `/features` / `/statuses` | Reference data |
| GET | `/api/v1/feedback/mine` | The caller's own submissions |
| GET | `/api/v1/feedback/board?sort=top\|new\|trending&type=&status=&cursor=` | The public board (keyset) |
| GET | `/api/v1/feedback/{id}` | Board detail (viewer flags included) |
| POST / DELETE | `/api/v1/feedback/{id}/vote` | Upvote / remove vote (idempotent) |
| GET / POST | `/api/v1/feedback/{id}/comments` | Comments (keyset) / add comment |
| DELETE | `/api/v1/feedback/{id}/comments/{commentId}` | Delete own comment |
| POST / DELETE | `/api/v1/feedback/{id}/subscription` | Subscribe / unsubscribe to status notifications |

## Entities

| Entity → table | Notes |
|---|---|
| `FeedbackEntity` → `feedback` | `vote_count` / `comment_count` are `int`, trigger-maintained, read-only in JPA; `status` DB-defaults to `submitted`; `response` is the team's official reply |
| `FeedbackVoteEntity` → `feedback_votes` | Composite PK (feedback, user); inserted with `ON CONFLICT DO NOTHING` |
| `FeedbackCommentEntity` → `feedback_comments` | Flat author UUID; bump-trigger maintains `comment_count` |
| `FeedbackSubscriptionEntity` → `feedback_subscriptions` | Composite PK; the status-notification opt-in |
| `Feedback{Type,Feature,Status}OptionEntity` | Seeded reference data |

Trending sort is a native query over the last-7-days vote counts with row-value keyset pagination;
`top`/`new` are JPQL with null-tolerant filters. Cursors are opaque base64url tokens
(`BoardCursor`).
