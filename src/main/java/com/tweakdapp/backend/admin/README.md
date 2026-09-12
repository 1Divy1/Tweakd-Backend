# admin module

The backend of the **"Tweakd." admin dashboard**: team management, the overview analytics, the
content-moderation queue, and the admin sides of feedback and support. Owns `admin_team_members`,
`moderation_cases`, and `moderation_actions`.

Nothing depends on this module, so its whole surface (controllers, services, DTOs) lives in
`internal/` — it exposes no public API. It orchestrates the others:
`admin → {report, posts, forums, profile, feedback, support, notification, shared}`.

## Staff accounts — separate from app accounts

Dashboard staff are **profile-less Supabase auth users**: same auth pool and JWT chain as the app,
but the `handle_new_user` trigger skips the `profiles` insert for them, so they don't exist in the
app (no search, no feed, no bans). Their identity — email, display name, avatar — lives on the
`admin_team_members` row itself, exposed to other modules via `shared/staff/StaffDirectory`
(implemented here). A person who is both an app user and staff has two logins with different
emails.

New members are **invited by email** (`POST /team`): `SupabaseAuthAdminClient` calls the GoTrue
admin API with the service-role key (`supabase.secret-key` ← `SUPABASE_SECRET_KEY` in `.env`) —
`POST /auth/v1/invite` carries `is_staff` in the user metadata (that is what the trigger keys on,
since it fires before anything can be patched) and the follow-up `PUT /auth/v1/admin/users/{id}`
sets `app_metadata.role = 'admin'`. Inviting an email that already has an auth user (app account
or staff) 409s — `StaffEmailInUseException`. Removing a member deletes the row and the auth user
(the team FK cascades from `auth.users`).

## Auth — two layers

1. **Security chain:** `/api/v1/admin/**` requires `ROLE_ADMIN`, i.e. the Supabase user's
   `app_metadata.role = 'admin'` (matcher in `shared/security/SecurityConfig`), set automatically
   at invite time.
2. **Capabilities:** every endpoint calls `AdminAccessService.require(userId, capability)`, which
   loads the caller's `admin_team_members` row (403 `NotTeamMemberException` if absent), checks the
   role→capability matrix (403 `MissingCapabilityException`), and touches `last_active_at`
   (throttled to one write / 5 min). The first such touch flips an `invited` row to `active`.

| Role | Capabilities |
|---|---|
| `owner` | everything |
| `senior_admin` | everything except TRANSFER_OWNERSHIP |
| `content_moderator` | REVIEW_CONTENT, WARN_BAN |
| `support_agent` | ANSWER_TICKETS (tickets **and** feedback admin) |
| `technical` | VIEW_ANALYTICS |

The overview page is gated on team membership only, so every member has a landing page.

Five capabilities are owner / senior-admin only for the same reason, and none of them is
moderation: `APPROVE_EVENTS` (publishing a user's event to the map), `MANAGE_ROADMAP` (telling the
community something is in development or shipped), `MANAGE_BADGES` (handing someone recognition no
rule can judge), `VERIFY_BUSINESSES` (putting a real company on the map — a commercial decision),
and `MANAGE_CONTESTS` (force-finishing pays out reputation and badges that cannot be cleanly taken
back). Each one publishes something or pays something out; taking content down is the opposite end
of the job.

## Moderation model

- **Cases** group all reports against one target: `moderation_cases` rows are upserted by a DB
  trigger on every report insert (`UNIQUE(target_type, target_id)`; a new report reopens a resolved
  case). The bigint id is the dashboard's "Case #4821". This module never inserts cases.
- **Decisions** all follow one shape: write the `moderation_actions` audit row → close out the
  report rows (`report` module) → apply the effect → resolve the case. For **remove**, the audit
  snapshot is written *before* the hard delete because report rows CASCADE away with the content.
- Content lookups/deletes go through each module's public service (`getXModerationSnapshot`,
  `deleteXAsModerator` — see `shared/moderation/ModerationContentDto`). Content that vanished while
  a case was open renders as `[deleted]`.
- **Severity heuristic:** HIGH = ≥5 reports or any severe reason (violence / hate / suicide /
  self-injury / harassment / illegal); MEDIUM = ≥2; LOW = 1.
- **Bans** delegate to `profile.banUser(profileId, until)` (temp via `banDays`, else permanent);
  enforcement is the profile module's banned-user interceptor. Warn / removal notify the author
  in-app via `notification`.

## REST endpoints (all under `/api/v1/admin`, ROLE_ADMIN + capability)

| Method | Path | Capability |
|---|---|---|
| GET | `/team` | member |
| POST | `/team` `{email, displayName, role}` (sends the Supabase invite mail) | MANAGE_TEAM |
| PATCH | `/team/{userId}` `{role}` | MANAGE_TEAM |
| DELETE | `/team/{userId}` (also deletes the staff auth user) | MANAGE_TEAM |
| GET | `/overview` | member |
| GET | `/moderation/queue?status=&type=&cursor=&size=` | REVIEW_CONTENT |
| GET | `/moderation/queue/counts` | REVIEW_CONTENT |
| GET | `/moderation/cases/{id}` | REVIEW_CONTENT |
| POST | `/moderation/cases/{id}/approve` | REVIEW_CONTENT |
| POST | `/moderation/cases/{id}/remove` | REVIEW_CONTENT |
| POST | `/moderation/cases/{id}/warn` (note required) | WARN_BAN |
| POST | `/moderation/cases/{id}/ban` (`banDays` optional) | WARN_BAN |
| POST | `/moderation/cases/{id}/escalate` | REVIEW_CONTENT |
| POST | `/moderation/users/{profileId}/unban` | WARN_BAN |
| GET | `/feedback?sort=&type=&status=&cursor=` | ANSWER_TICKETS |
| GET | `/feedback/stats` | ANSWER_TICKETS |
| GET | `/feedback/statuses` | member |
| POST | `/feedback/{id}/response` | ANSWER_TICKETS |
| PATCH | `/feedback/{id}/status` (→ notifies author + subscribers) | ANSWER_TICKETS |
| GET | `/support/tickets?status=&cursor=` | ANSWER_TICKETS |
| GET | `/support/tickets/stats` | ANSWER_TICKETS |
| GET | `/support/tickets/{id}` | ANSWER_TICKETS |
| POST | `/support/tickets/{id}/messages` (→ notifies requester) | ANSWER_TICKETS |
| PATCH | `/support/tickets/{id}` (priority / status / assignee) | ANSWER_TICKETS |
| GET | `/badges` (catalogue + holder counts, retired included) | MANAGE_BADGES |
| POST | `/badges/{badgeId}` (create; 409 if the code is taken) | MANAGE_BADGES |
| PUT | `/badges/{badgeId}` (edit; `available: false` retires) | MANAGE_BADGES |
| DELETE | `/badges/{badgeId}` (409 while anyone holds it) | MANAGE_BADGES |
| GET | `/badges/holders/{userId}` (the only read that returns `granted_by`) | MANAGE_BADGES |
| POST | `/badges/{badgeId}/holders/{userId}` (grant by hand) | MANAGE_BADGES |
| DELETE | `/badges/{badgeId}/holders/{userId}` (take it back) | MANAGE_BADGES |
| GET | `/badges/artwork-base-url` (public URL prefix, for previewing a typed R2 key) | MANAGE_BADGES |
| POST | `/team/{userId}/transfer-ownership` (caller drops to `senior_admin`) | TRANSFER_OWNERSHIP |
| GET | `/map-events?status=&cursor=&size=` (oldest first; default `pending`) | APPROVE_EVENTS |
| GET | `/map-events/counts` | APPROVE_EVENTS |
| GET | `/map-events/{id}` | APPROVE_EVENTS |
| POST | `/map-events/{id}/approve` | APPROVE_EVENTS |
| POST | `/map-events/{id}/reject` `{reason}` (creator can edit → resubmits) | APPROVE_EVENTS |
| DELETE | `/map-events/{id}` (cascades; removes the R2 cover) | APPROVE_EVENTS |
| GET | `/feedback-feed?type=&status=&include_removed=&cursor=` | MANAGE_ROADMAP |
| GET | `/feedback-feed/top?limit=` | MANAGE_ROADMAP |
| GET | `/feedback-feed/types`, `/feedback-feed/statuses` | member |
| PATCH | `/feedback-feed/{id}/status` (→ notifies the author) | MANAGE_ROADMAP |
| PUT | `/feedback-feed/{id}/response` (empty body clears; silent) | MANAGE_ROADMAP |
| DELETE | `/feedback-feed/{id}` (soft-remove, row kept for audit) | MANAGE_ROADMAP |
| GET | `/businesses?verification_status=&active_status=&cursor=` | VERIFY_BUSINESSES |
| GET | `/businesses/counts`, `/businesses/{id}` | VERIFY_BUSINESSES |
| POST | `/businesses/{id}/verify` | VERIFY_BUSINESSES |
| POST | `/businesses/{id}/reject` `{reason}` | VERIFY_BUSINESSES |
| PATCH | `/businesses/{id}/active-status` `{activeStatus}` (`active` / `suspended`) | VERIFY_BUSINESSES |
| GET | `/contests?status=&limit=` (default `open`, longest-running first) | MANAGE_CONTESTS |
| GET | `/contests/counts` | MANAGE_CONTESTS |
| POST | `/contests/{id}/finish` (force-finish; pays the podium) | MANAGE_CONTESTS |

Team/owner rules: `owner` is never assignable via add/re-role, and the owner's row can be neither
re-roled nor removed. The single exception is `POST /team/{userId}/transfer-ownership`: inside one
transaction the caller is demoted to `senior_admin` and the target promoted — in that order,
because `admin_team_members_single_owner_idx` (partial unique index, added 2026-09-12) is checked
per statement. Both rows are taken `FOR UPDATE` in uuid order, so two transfers racing serialise
rather than leaving the dashboard ownerless.

## Businesses and contests — two writes that are not moderation

Neither the `business` nor the `mapevents` module had an outside way to make these decisions, so
they were SQL statements typed by hand:

- **Business verification.** `business_accounts` rows are only visible to the app when
  `active_status = 'active'` **and** `verification_status = 'verified'`. The business module stays
  read-only otherwise — nothing here creates, edits or deletes a business — and the three status
  writes it now exposes are called only from `AdminBusinessesController`. `rejection_reason`,
  `reviewed_by` and `reviewed_at` were added for this (2026-09-12 migration); `reviewed_by` holds a
  **staff auth id**, so it has no FK and nothing joins it to `profiles`.
- **Contest force-finish.** Since `ContestClock` was deleted (see `CONTESTS_MANUAL_LIFECYCLE.md`)
  nothing closes a contest on a timer: an organizer taps, or the event's own finish cascades. An
  organizer who walks away leaves a contest taking votes forever, and no organizer means nobody in
  the app can end it. `POST /contests/{id}/finish` runs the same `ContestFinalizer.finalizeLocked`
  the organizer's own button does, under the same row lock, with the same "a cancelled event pays
  nobody" rule. The staff id lands in `contests.finished_by`, which has no FK for that reason.

## Overview analytics

`AdminOverviewService` runs read-only native aggregates via `JdbcClient` over other modules'
tables (posts today/week/month + 30-day series, content mix, new profiles per week ×8, badge
counts) — the **documented exception** to strict table ownership: read-only, aggregate-only.
DAU / MAU / session / churn are deferred until activity tracking exists.

The sidebar's six badge numbers (pending cases, open tickets, new feedback, pending map events,
un-triaged roadmap requests, businesses awaiting verification) come back in **one** statement:
every branch is an equality count on an indexed status column, and the dashboard refetches this on
every page, so six round trips would cost six times the latency for nothing. Two partial indexes
were added for the newer branches in the 2026-09-12 migration.
