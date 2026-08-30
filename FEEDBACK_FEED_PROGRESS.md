# Feedback Feed — progress

Branch: `feedback-feed`. Started 2026-08-17.

Public community feedback feed: users post a short message with a category (bug / feature request /
feature improvement), everyone can up/down vote, staff (owner) manage status and write an optional
official response. Mobile mocks: feed with NEWEST / POPULAR / OLDEST tabs + a "Completed requests"
section behind a redirect chip.

## Status

| # | Step | State |
|---|---|---|
| 0 | Schema discovery (Supabase `feedback_feed_*`) | DONE |
| 1 | Owner decisions round 1 + 2 | DONE |
| 2 | Owner decisions round 3 (notify / capability / sort) | DONE |
| 3 | Migration: `completed_at` column + stamping + index | DONE — applied as `feedback_feed_completed_at` |
| 4 | Module skeleton (`feedbackfeed`, package-info) | DONE |
| 5 | Entities + repositories | DONE |
| 6 | DTOs + exceptions | DONE |
| 7 | Service impl (feed, completed list, submit, delete, vote) | DONE |
| 8 | User controller `/api/v1/feedback-feed/**` | DONE |
| 9 | Admin surface (top-N, status, response, soft delete) + `MANAGE_ROADMAP` | DONE |
| 10a | `FeedbackFeedMessageRepositoryIT` (11 tests) | DONE — green |
| 10b | `FeedbackFeedServiceImplTest` (31 tests) | DONE — green |
| 10c | `FeedbackFeedControllerWebTest` (19 tests) | DONE — green |
| 11 | Docs (CONTEXT.md, module README) | DONE |

Full suite: 618 tests, 616 pass. The 2 failures are **pre-existing** in
`MapEventsServiceImplTest` (`creatingAnEventWithRules…`, `creatingAnEventWithoutRules…`, both
NPE on a null event) — verified by stashing this branch's changes and re-running; untouched here.

Also done: `scripts/dump-schema.sh` re-run (test schema carries `completed_at`, the stamping
trigger and the new index), and `src/test/resources/db/seed.sql` now seeds the two
`feedback_feed_*` reference tables (RESTRICT FKs).

## Endpoints

**User** — `/api/v1/feedback-feed`

| Method | Path | Description |
|---|---|---|
| GET | `/types` / `/statuses` | Reference data |
| GET | `?sort=newest\|popular\|oldest&cursor=&size=` | The main feed (non-completed) |
| GET | `/completed?cursor=&size=` | The "Completed requests" section, latest ship date first |
| GET | `/{messageId}` | One card |
| POST | `` | Publish → `201` + the card |
| DELETE | `/{messageId}` | Delete own (hard, only while `sent`) → `204`, else `409` |
| POST | `/{messageId}/vote` | Cast `{"value": 1\|-1}`; same value again withdraws → the card |
| DELETE | `/{messageId}/vote` | Withdraw → the card |

**Admin** — `/api/v1/admin/feedback-feed`, all gated on `MANAGE_ROADMAP`

| Method | Path | Description |
|---|---|---|
| GET | `?type=&status=&include_removed=&cursor=&size=` | Full list, newest first |
| GET | `/top?limit=3` | Most-voted unshipped requests |
| GET | `/statuses` | Status picker (any team member) |
| PATCH | `/{messageId}/status` | Move along the roadmap (trigger notifies the author) |
| PUT | `/{messageId}/response` | Write / clear the official reply (silent) |
| DELETE | `/{messageId}` | Staff removal, soft → `204` |

## Existing schema (owned by Supabase — do not recreate)

`feedback_feed_messages`
- `id` uuid PK default `gen_random_uuid()`
- `author_id` uuid NOT NULL → `profiles(id)` ON DELETE CASCADE
- `message` text NOT NULL
- `type` text NOT NULL → `feedback_feed_feedback_types(id)` (`bug`, `feature_request`, `feature_improvement`)
- `status` text NOT NULL default `'sent'` → `feedback_feed_status_options(id)` (`sent`, `under_development`, `completed`)
- `up_votes`, `down_votes`, `net_votes` int NOT NULL default 0 — **trigger-maintained, read-only in JPA**
- `is_deleted` boolean NOT NULL default false
- `staff_response_message` text NULL
- `created_at` timestamptz NOT NULL default `now()`

`feedback_feed_votes`
- PK `(user_id, message_id)`; `user_id` → `auth.users(id)`, `message_id` → `feedback_feed_messages(id)`, both CASCADE
- `vote_type` smallint NOT NULL CHECK in (1, -1)
- `created_at`, `updated_at` timestamptz NOT NULL default `now()`

Reference tables `feedback_feed_feedback_types(id, type, created_at)` and
`feedback_feed_status_options(id, status, created_at)` — no `sort_order`, no `color`.

### Triggers already in the DB (backend must NOT duplicate their effects)

| Trigger | Effect |
|---|---|
| `feedback_feed_votes_counts_trg` | Maintains `up_votes` / `down_votes` / `net_votes` on INSERT / UPDATE / DELETE of a vote |
| `feedback_feed_votes_block_completed_trg` | Raises an exception on any vote write when the message status is `completed` |
| `feedback_feed_messages_notify_author_trg` | AFTER UPDATE OF status → inserts a `feedback_status_changed` row into `notifications` for the author |

Consequence: after any vote or status write the entity is stale — reload before assembling the DTO
(same `reloadAndAssemble` pattern as `mapevents`).

### Indexes (shape the queries)

- `idx_feedback_feed_messages_popular` — `(net_votes DESC, id DESC) WHERE status <> 'completed'`
- `idx_feedback_feed_messages_active_created` — `(created_at DESC, id DESC) WHERE status <> 'completed'`
- `idx_feedback_feed_messages_completed_created` — `(created_at DESC, id DESC) WHERE status = 'completed'`

Keyset pagination must match these column pairs.

## Owner decisions (confirmed — do not re-ask)

1. **New module** `feedbackfeed`. The old `feedback` module (public board with comments /
   subscriptions) **stays untouched** — too much migration to remove now, owner may drop it later.
2. **Completed items live in their own section**, not in the main feed. Main feed = `status <> 'completed'`
   with NEWEST / POPULAR / OLDEST sorts. `under_development` items stay in the main feed and just
   render an "IN PROGRESS" pill.
3. **Top-3 most voted = a plain read**, no persisted "featured" state, no migration.
4. **Voting**: same direction again = remove the vote (toggle off); opposite direction = switch;
   author may vote on their own message. No self-vote restriction.
5. **`completed_at` column is being added** (migration below) — completed cards render "shipped 3d ago".
6. **Both texts are always returned**: `message` (the author's original) and `staffResponse`. The
   client renders the staff response *below* the original message. The backend never substitutes one
   for the other. The mock showing only the staff wording is out of date.
7. **No completed counter** on the feed response — the banner count is ignored.
8. **Delete semantics differ by actor**:
   - author deleting their own message → **hard delete** from the DB (votes cascade), but **only
     while the status is still `sent`**. Once staff move it to `under_development` or `completed`
     the author can no longer touch it (`409`) — it is a public roadmap entry other people have
     voted on. Staff can still remove it, softly.
   - staff removing a message (spam / abuse) → **soft delete**, `is_deleted = true`.
   No edit endpoint for authors.

9. **Completed list is ordered by `completed_at DESC`** (most recently shipped first) — it reads as a
   changelog. Needs a matching partial index, added in the same migration.
10. **New `MANAGE_ROADMAP` capability**, held by `owner` + `senior_admin` only, gates every
    feedback-feed admin operation (top-N, status, response, soft delete). Same reasoning as
    `APPROVE_EVENTS`: setting a status is a product-roadmap statement, not user support. Support
    agents deliberately do **not** get it (they keep `ANSWER_TICKETS` for the old board).
11. **A staff response written without a status change is silent** — no notification. The only
    author notification is the existing DB trigger on status change. No new notification type.

## Notes / gotchas encountered

- **`now()` is the transaction clock, not the statement clock.** Two messages completed inside one
  transaction get an identical `completed_at`, so the archive falls back to the id tiebreaker. Fine
  in production (one status change = one transaction); it broke the ordering IT until the fixture
  stamped explicit ship dates. Not worth switching the trigger to `clock_timestamp()`.
- **Multi-word query parameters are snake_case in this API** (`proximity_lat`, `radius_km`,
  `user_ids`), matching the global Jackson `SNAKE_CASE` strategy. `include_removed` follows suit.
- **Nulls are serialized** — no `default-property-inclusion` is configured, so `my_vote` and
  `staff_response` appear as `null` rather than being omitted.
- `MANAGE_ROADMAP` needed no edit to `AdminRole`: `owner` is `allOf` and `senior_admin` is
  `complementOf(TRANSFER_OWNERSHIP)`, so adding the enum constant granted exactly the intended two.
