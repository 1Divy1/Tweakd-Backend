# Admin Dashboard — Implementation Plan & Progress

Backend for the "Tweakd." admin dashboard web app (screenshots reviewed 2026-07-11: Overview ×2,
Content Moderation, Feedback, Support Tickets, Moderators ×2). This file is the source of truth if
work is interrupted — it captures **decisions made with the owner**, the full design, and a live
checklist.

## Decisions (confirmed with the owner — do NOT re-ask)

| Topic | Decision |
|---|---|
| Admin auth | Moderators are normal Supabase auth users with `app_metadata.role = 'admin'` (passes the `/api/v1/admin/**` gate) **plus** a row in `admin_team_members` holding the fine-grained role: `owner`, `senior_admin`, `content_moderator`, `support_agent`, `technical`. Backend enforces per-role capabilities from that table. |
| Activity analytics | DAU / MAU / avg session / churn are **deferred** (no activity tracking exists). Overview ships only what's derivable today: posts created, content mix, new profiles, live mod queue, badge counts. |
| Feedback board | **Full public board in the app**: browse all feedback, upvote, comment, subscribe for status notifications. |
| Notifications | **Generic `notification` module** (polymorphic `notifications` table); feedback status changes are the first producer, ticket replies / mod warnings also use it. In-app only (no push). |
| Feedback statuses | Full lifecycle: `submitted → under_review → investigating → planned → in_development → testing → released`, plus `fixed` (bugs) and `declined`. Existing rows migrate: `in_review→under_review`, `resolved→released`, `closed→declined`. |
| Tickets | User-facing side built too (create / reply / list mine). **Priority is admin-set** (low/normal/high/urgent). Business badge from `profiles.is_business`. |
| Content removal | **Hard delete** via each module's existing delete logic + snapshot of removed text kept in `moderation_actions` audit table. |
| Bans | `profiles.is_banned` + `banned_until` (temp bans) + request interceptor rejecting banned users' requests with 403 (60s in-memory cache). Recorded in `moderation_actions`. |

## Architecture

New modules: `notification` (leaf), `support`, `admin`. Extended: `feedback`, `profile`, `posts`,
`forums`, `report`, `shared` (security matcher only).

Dependency direction (no cycles): `admin → {report, posts, forums, profile, feedback, support,
notification, shared}`; `support/notification/feedback → {profile?, shared}`; everything else unchanged.

- Admin REST base: `/api/v1/admin/...` — SecurityConfig matcher updated to require `ROLE_ADMIN` there.
- Fine-grained capability checks (per Moderators-page matrix) via `AdminAccessService` reading
  `admin_team_members`: capabilities = REVIEW_CONTENT, WARN_BAN, ANSWER_TICKETS, REFUNDS,
  VIEW_ANALYTICS, MANAGE_TEAM, TRANSFER_OWNERSHIP mapped per role in code.
- Moderation "cases" group reports by (target_type, target_id): `moderation_cases` row upserted by
  DB trigger on every report insert; numeric id gives the dashboard's "Case #4821".
- Severity heuristic (HIGH/MED/LOW) computed in code from report count + reason.
- Admin overview analytics = read-only native queries in `admin/internal` (documented exception to
  table ownership; aggregates only).
- Cross-module content lookups for moderation: each content module exposes
  `ModerationContentDto`-shaped snapshot + `deleteXAsModerator` methods on its public service.
- Notifications orchestration lives in the admin module (fetch subscriber ids from feedback, then
  `notificationService.push(...)`) so `feedback` does not depend on `notification`.

## Supabase tables (migrations)

1. `admin_team_members(user_id PK→profiles, role, status invited|active, invited_by, created_at, last_active_at)`
2. Feedback: reseed `feedback_status_options` (9 statuses w/ colors) + migrate `feedback.status`;
   add `feedback.vote_count`, `feedback.comment_count`; new `feedback_votes` (PK feedback+user, count trigger),
   `feedback_comments` (count trigger), `feedback_subscriptions` (PK feedback+user).
3. `notifications(id, user_id→profiles, type, title, body, payload jsonb, is_read, created_at)` + indexes.
4. `support_ticket_categories` (seeded: account, billing, technical, content, other),
   `support_tickets(id, user_id, subject, category, priority low|normal|high|urgent, status open|awaiting_user|resolved, assigned_to, created_at, updated_at, last_message_at)`,
   `support_ticket_messages(id, ticket_id, sender_id, is_staff, content, created_at)` + last_message_at trigger.
   Status semantics: user message → `open` (waiting on staff); staff reply → `awaiting_user`; resolve → `resolved`.
5. `moderation_cases(id bigint identity, target_type, target_id, status open|escalated|resolved, resolution, resolved_by, resolved_at, created_at, UNIQUE(target_type,target_id))`
   + insert-triggers on all 5 report tables; `moderation_actions(id, moderator_id, action, target_type, target_id, target_author_id, content_snapshot, note, created_at)`.
6. `profiles`: + `is_banned bool`, `banned_until timestamptz`, `created_at timestamptz` (backfilled from `auth.users.created_at` — needed for "account age" in case detail).

## Endpoints

### User-facing (app)
- Notifications: `GET /api/v1/notifications` (cursor), `GET /api/v1/notifications/unread-count`,
  `POST /api/v1/notifications/{id}/read`, `POST /api/v1/notifications/read-all`
- Feedback board: `GET /api/v1/feedback/board?sort=top|new|trending&type=&status=&cursor=`,
  `GET /api/v1/feedback/{id}`, `POST|DELETE /api/v1/feedback/{id}/vote`,
  `GET|POST /api/v1/feedback/{id}/comments`, `DELETE /api/v1/feedback/{id}/comments/{commentId}` (own),
  `POST|DELETE /api/v1/feedback/{id}/subscription`
- Support: `GET /api/v1/support/categories`, `POST /api/v1/support/tickets`,
  `GET /api/v1/support/tickets/mine`, `GET /api/v1/support/tickets/{id}`,
  `POST /api/v1/support/tickets/{id}/messages`

### Admin (`/api/v1/admin/**`, ROLE_ADMIN + team membership)
- Team: `GET /team`, `POST /team` (add existing user by username + role), `PATCH /team/{userId}`
  (role), `DELETE /team/{userId}`; owner-only manage; ownership transfer TODO.
- Overview: `GET /overview` (posts today/week/month + 30d series, content mix, new-profiles 8wk,
  newest open cases, badge counts).
- Moderation: `GET /moderation/queue?type=&sort=&cursor=`, `GET /moderation/queue/counts`,
  `GET /moderation/cases/{id}`, `POST /moderation/cases/{id}/approve|remove|warn|ban|escalate`.
- Feedback: `GET /feedback?sort=&type=&status=&cursor=`, `GET /feedback/stats`,
  `POST /feedback/{id}/response`, `PATCH /feedback/{id}/status` (→ notifies subscribers + author).
- Support: `GET /support/tickets?status=&cursor=`, `GET /support/tickets/{id}`,
  `POST /support/tickets/{id}/messages` (staff reply → notifies user), `PATCH /support/tickets/{id}`
  (assign / priority / status).

## Checklist

- [x] 1. This progress file
- [x] 2. Supabase migrations — ALL APPLIED to project `fybgmaigzidhbmhbgfhu` (admin_team_members;
      feedback_board_and_lifecycle; notifications; support_tickets; moderation_cases_and_actions;
      profiles_ban_and_created_at)
- [x] 3. `notification` module
- [x] 4. `feedback` extensions (board, votes, comments, subscriptions, admin ops)
- [x] 5. `support` module
- [x] 6. `profile` ban support + snapshot + banned-user interceptor
- [x] 7. `posts`/`forums` moderation snapshots + moderator deletes
- [x] 8. `report` admin read/resolve methods
- [x] 9. `admin` module (team, overview, moderation, feedback admin, support admin) + SecurityConfig
- [x] 10. Build + ModularityTests green (`./mvnw test`: 3/3, incl. live-schema validation)
- [x] 11. READMEs + CONTEXT.md + memory updated

**STATUS: COMPLETE** (2026-07-11). All endpoints are implemented and the schema validates against
the live Supabase DB; endpoints are not yet exercised end-to-end from the dashboard web app.
Remember: dashboard users need `app_metadata.role = 'admin'` set on their Supabase auth user
(manual step) *and* an `admin_team_members` row — seed the owner row for your own account:
`insert into admin_team_members (user_id, role, status) values ('<your-uuid>', 'owner', 'active');`

## Deferred / TODO later

- DAU / WAU / MAU, avg session length, churn (needs activity/session tracking — decide approach later)
- Push notifications (in-app only for now)
- Email-based moderator invites via Supabase Auth admin API (currently: add existing user by username);
  "invitee sets password + 2FA on first sign-in" flow
- Ownership transfer endpoint
- Auto-toxicity score on moderation cases (dashboard shows one — no ML scoring backend yet)
- Refund processing (support "Refunds" scope is a capability flag only; no billing integration)
- Canned replies for support (dashboard button exists; table + endpoints not built yet)
- Ticket search / feedback search endpoints (dashboard search bars) — can be added as query params
- Mod-queue filters "Most reported / Severity" sorting refinements; "escalate to senior admin"
  currently only flags the case — no assignment/notification to seniors yet
