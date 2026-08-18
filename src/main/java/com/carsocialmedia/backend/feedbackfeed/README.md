# feedbackfeed module

Owns the app's **public community feedback board**: short messages users publish under one of three
categories, which everyone can up- or downvote, and which staff move along a small roadmap and
optionally reply to. Tables: `feedback_feed_messages`, `feedback_feed_votes`, plus the reference
tables `feedback_feed_feedback_types` and `feedback_feed_status_options`.

Depends on `profile` (author cards) and `shared`. It deliberately does **not** depend on
`notification` — the author's status-change notification is written by a Supabase trigger, so there
is no application fan-out to orchestrate.

## Not to be confused with the `feedback` module

`feedback` is the older board behind the admin dashboard (nine-status lifecycle, comments,
subscriptions, upvotes only). This module is the in-app community feed the mobile client shows. The
two coexist on purpose; retiring the old one is a separate job.

## Lifecycle

`sent → under_development → completed`. Completed messages leave the main feed for their own
"Completed requests" section, ordered by ship date, and the database refuses any vote against them.

## Public API — `FeedbackFeedService`

User side: `listTypes`, `listStatuses`, `getFeed(sort)` (`newest` / `popular` / `oldest`),
`getCompleted`, `getMessage`, `submit`, `deleteOwn`, `vote`, `removeVote`.

Admin (no auth here — the `admin` module gates it on `MANAGE_ROADMAP`): `listAll`, `listTopVoted`,
`updateStatus`, `respond`, `removeAsStaff`.

## REST endpoints

`/api/v1/feedback-feed`

| Method | Path | Description |
|---|---|---|
| GET | `/types` / `/statuses` | Reference data |
| GET | `?sort=newest\|popular\|oldest&cursor=&size=` | The main feed (excludes completed) |
| GET | `/completed?cursor=&size=` | The completed section, latest ship date first |
| GET | `/{messageId}` | One card, with the caller's vote resolved |
| POST | `` | Publish → `201` + the card |
| DELETE | `/{messageId}` | Delete own, only while `sent` → `204` |
| POST | `/{messageId}/vote` | `{"value": 1\|-1}` → the updated card |
| DELETE | `/{messageId}/vote` | Withdraw → the updated card |

`/api/v1/admin/feedback-feed` (owned by the `admin` module, all gated on `MANAGE_ROADMAP`)

| Method | Path | Description |
|---|---|---|
| GET | `?type=&status=&include_removed=&cursor=&size=` | Full list, newest first |
| GET | `/top?limit=3` | Most-voted unshipped requests |
| GET | `/statuses` | Status picker (any team member) |
| PATCH | `/{messageId}/status` | Move along the roadmap |
| PUT | `/{messageId}/response` | Write / clear the official reply |
| DELETE | `/{messageId}` | Staff removal (soft) → `204` |

## Entities

| Entity → table | Notes |
|---|---|
| `FeedbackFeedMessageEntity` → `feedback_feed_messages` | `up_votes` / `down_votes` / `net_votes` are `int`, trigger-maintained, read-only in JPA; `status` DB-defaults to `sent`; `completed_at` is trigger-stamped and read-only |
| `FeedbackFeedVoteEntity` → `feedback_feed_votes` | Composite PK (user, message); `vote_type` is `smallint` ±1 |
| `FeedbackFeed{Type,Status}OptionEntity` | Seeded reference data — no `sort_order` column, so roadmap order lives in `FeedbackFeedServiceImpl.STATUS_ORDER` |

## Behaviour worth knowing

**Voting is a three-way toggle.** Same direction again withdraws the vote, the opposite direction
switches it in place, and authors may vote on their own messages. The counters are maintained by
`trg_feedback_feed_update_vote_counts`, never by the app.

**Deletion is asymmetric.** An author deleting their own message **hard deletes** the row (votes
cascade), and only while it is still `sent` — once staff move it to `under_development` or
`completed` the author gets a `409`, because by then it is a public roadmap entry other people have
voted on. `is_deleted` is for *staff* removing spam or abuse, which keeps the row for audit while
hiding it from every read, and works at any status.

**Both texts always come back.** `message` (the author's) and `staffResponse` are separate DTO
fields; the client renders the response beneath the message. The backend never substitutes one for
the other.

**Every write ends in a reload.** Three triggers do work the service does not repeat — vote counts,
the author's status-change notification, and the `completed_at` stamp — so `reloadAndAssemble`
flushes and re-reads before building the response.

## Supabase trigger dependencies

| Trigger | Effect |
|---|---|
| `feedback_feed_votes_counts_trg` | Maintains `up_votes` / `down_votes` / `net_votes` |
| `feedback_feed_votes_block_completed_trg` | Rejects any vote write against a completed message (the service pre-checks so callers get a 409, not a SQL error) |
| `feedback_feed_messages_notify_author_trg` | Inserts a `feedback_status_changed` notification for the author on a status change |
| `feedback_feed_messages_stamp_completed_at_trg` | Stamps `completed_at` on entry to `completed`, clears it on the way back out |

## Pagination

Keyset, with opaque base64url cursors (`FeedbackFeedCursor`) in two shapes — a timestamp for the
time-ordered lists, a vote score for `popular`. The token names its own shape, so replaying a
`popular` cursor against `newest` is a `400` rather than silent nonsense. The paged reads are native
queries using Postgres row-value comparisons that match the table's composite partial indexes; every
cursor parameter is explicitly `cast(...)`, without which a null cursor (i.e. every first page)
fails with "could not determine data type of parameter".
