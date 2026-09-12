# Admin Dashboard — Catch-up Roadmap

> **Status: implemented, 2026-09-12.** Every phase below is built, on both sides,
> except the Phase 6 items the owner deferred (push broadcast, reputation admin).
> What each phase actually shipped is summarised in § 7; the phase write-ups are
> kept as the record of why each piece looks the way it does.
>
> **Two Supabase migrations must be applied by hand before deploying:**
> `20260912120000_business_admin_review.sql` (business review columns + two
> partial indexes) and `20260912130000_admin_single_owner.sql` (single-owner
> index). `ddl-auto: validate` means the first one is start-up blocking — the
> entity now maps `rejection_reason`, `reviewed_by`, `reviewed_at`.

The admin dashboard (`Tweakd-Dashboard/`, React 19 + TS + Tailwind 4 + Vite) was built and
wired to the backend on **2026-07-12** and has had **one commit since** ("Initial commit…").
The backend has shipped six feature waves since that date. This file lists what the dashboard
is missing, and the work — backend **and** web frontend — needed to close the gap.

**Answer to "is it up to date?": no.** Three admin API surfaces that already exist in the
backend have no UI at all, and one shipped feature area (business accounts) has no admin API
to build a UI against.

**Written:** 2026-09-12 · **Backend HEAD:** `949c6c8` · **Dashboard HEAD:** `7ee5db3`

Related docs: [`CONTEXT.md`](CONTEXT.md), the admin module
[README](src/main/java/com/tweakdapp/backend/admin/README.md),
[`EVENT_CONTESTS.md`](EVENT_CONTESTS.md),
[`CONTESTS_MANUAL_LIFECYCLE.md`](CONTESTS_MANUAL_LIFECYCLE.md),
and `Tweakd-Dashboard/PROGRESS.md`.

---

## 1. What shipped in the backend after the dashboard was built

| Date | Commit | Feature | Admin surface? | In dashboard? |
|---|---|---|---|---|
| 2026-08-06 | `0d3695f` | Business accounts | **none — missing** | ❌ |
| 2026-08-11 | `b977ab8` | Map events (+ `APPROVE_EVENTS`) | `/api/v1/admin/map-events` | ❌ |
| 2026-08-17 | `d9b9b40` | Feedback feed (+ `MANAGE_ROADMAP`) | `/api/v1/admin/feedback-feed` | ❌ |
| 2026-08-22 | `cfa4798` | Package rename `com.tweakdapp.backend` | n/a (no wire change) | n/a |
| 2026-08-30 | `64d9a9f` | Push notifications (FCM) | none | ❌ (low value) |
| 2026-09-03 | `05ff3a4` | Reputation + badges (+ `MANAGE_BADGES`) | `/api/v1/admin/badges` | ❌ |
| 2026-09-05 | `b13083f` | QR car sharing / public car page | none needed | — |
| 2026-09-09 | `7ee2600` | Event contests | none (organizer-driven) | ❌ |

Three new capabilities exist in `Capability.java` — `APPROVE_EVENTS`, `MANAGE_ROADMAP`,
`MANAGE_BADGES` — that the dashboard's Moderators permission matrix (static in
`ModeratorsPage.tsx`) does not list, and no page consumes.

## 2. Gap summary

**Backend endpoints with no UI (build frontend only):**
- Map-event review queue — 6 endpoints, `APPROVE_EVENTS`.
- Feedback-feed / roadmap — 6 endpoints, `MANAGE_ROADMAP`.
- Badge catalogue + grants — 7 endpoints, `MANAGE_BADGES`.

**Backend gaps (build backend, then UI):**
- No admin API for **business account verification**, although `verification_status` has
  `pending` / `rejected` / `suspended` states and only `verified` businesses appear in the app.
  Today that flip is a hand-written SQL statement in Supabase.
- No badge-artwork upload endpoint — `BadgeUpsertRequest` takes R2 object keys, and
  `StorageController` has no shared-app-assets presign route, so a badge cannot be created
  end-to-end from the dashboard.
- `GET /admin/feedback-feed` has no sibling `/types` (categories) route; only the app-facing
  `GET /api/v1/feedback-feed/types` exists.
- `OverviewDto.BadgeCountsDto` carries three counters (`pendingCases`, `openTickets`,
  `newFeedback`); the sidebar needs a fourth and fifth for pending map events and new
  feedback-feed requests.
- No admin visibility into **event contests** (podium disputes, stuck `open` contests after
  the `ContestClock` removal — see `CONTESTS_MANUAL_LIFECYCLE.md`).
- `TRANSFER_OWNERSHIP` capability exists with no endpoint (already noted as TODO in
  `Capability.java`).
- Still absent, as at 2026-07-12: DAU/MAU/session/churn analytics, ticket/feedback search,
  canned replies.

**Frontend-wide gaps:**
- Permission matrix and `labels.ts` predate the three new capabilities.
- `targetTypeLabels` covers `post / comment / forum_thread / forum_thread_reply / profile`,
  which still matches `ReportTarget` — **no change needed**, but re-check if map events or
  cars ever become reportable.
- Dashboard repo is now under git (single commit) — the old PROGRESS note saying otherwise
  is stale.

---

## Phase 1 — Map events review (highest value)

Organizer-submitted events sit in `pending` until someone approves them; there is currently
no way to do that outside SQL. This is the most user-blocking gap.

### Backend
Nothing required. Endpoints exist in `AdminMapEventsController`, all gated on
`Capability.APPROVE_EVENTS` (owner + senior_admin only):

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/admin/map-events?status&cursor&size` | keyset page, **oldest first**; `status` defaults to `pending`, also `accepted` / `rejected` |
| GET | `/api/v1/admin/map-events/counts` | `{ "pending": n }` |
| GET | `/api/v1/admin/map-events/{eventId}` | full `MapEventDto`, any approval state |
| POST | `/api/v1/admin/map-events/{eventId}/approve` | → `MapEventDto` |
| POST | `/api/v1/admin/map-events/{eventId}/reject` | body `EventRejectionRequest { reason }` |
| DELETE | `/api/v1/admin/map-events/{eventId}` | 204, cascades + deletes the R2 cover |

*Optional (do it with Phase 5):* fold `pending_events` into `OverviewDto.badges` so the
sidebar badge does not need a second request.

### Frontend (`Tweakd-Dashboard`)
1. `src/api/types.ts` — add `MapEventSummaryDto` (`id, title, category_id, category_label,
   location_name, lat, lng, starts_at, ends_at, cover_image_url, status, approval_status,
   rejection_reason, max_participant_capacity, attendees_count, attending_cars_count,
   creator: ProfileRefDto, created_at`), `MapEventDto`, and reuse
   `CursorPage<T>` for `MapEventPageDto` (`items` + `next_cursor` — same shape).
2. `src/api/admin.ts` — `getEventQueue`, `getEventCounts`, `getEvent`, `approveEvent`,
   `rejectEvent(reason)`, `deleteEvent`.
3. `src/pages/EventsPage.tsx` — status tabs (Pending / Accepted / Rejected), cursor
   "Load more", list rows with cover thumbnail (`safeUrl` + `referrerPolicy="no-referrer"`,
   as elsewhere), detail panel showing map coords + time window + capacity + creator,
   Approve / Reject (reason required, mirror the warn/ban note dialog) / Delete
   (two-step confirm, matching `ModeratorsPage` delete).
4. Route `/events` in `router.tsx`; sidebar entry under **Moderation** (icon `MapPin`),
   `badgeKey: 'pending_events'` once Phase 5 lands, otherwise from `/counts`.
5. Gate write controls on `APPROVE_EVENTS` the way `ModeratorsPage` gates on `MANAGE_TEAM`;
   the whole nav item hides for roles without it (content_moderator, support_agent,
   technical).

**Effort:** ~1 day frontend.

---

## Phase 2 — Feedback feed / roadmap

`/feedback` in the dashboard is the **older** feedback module (`/admin/feedback`). The
community feedback feed shipped 2026-08-17 as a separate module with its own voting and
roadmap statuses, and is entirely unmanaged from the UI.

### Backend
Endpoints exist in `AdminFeedbackFeedController`, gated on `MANAGE_ROADMAP` (except
`/statuses`, which only requires team membership):

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/admin/feedback-feed?type&status&include_removed&cursor&size` | newest first, every status |
| GET | `/api/v1/admin/feedback-feed/top?limit=3` | most-voted unshipped requests |
| GET | `/api/v1/admin/feedback-feed/statuses` | roadmap stages for the picker |
| PATCH | `/api/v1/admin/feedback-feed/{id}/status` | `{ status }`; notifies the author via DB trigger |
| PUT | `/api/v1/admin/feedback-feed/{id}/response` | `{ response }` (≤1000 chars; empty clears). Silent — no notification |
| DELETE | `/api/v1/admin/feedback-feed/{id}` | 204, soft-remove (row kept for audit), idempotent |

**To add:** `GET /api/v1/admin/feedback-feed/types` returning `List<FeedbackCategoryDto>`
(`access.requireMember`, delegating to `feedbackFeedService.listTypes()`) so the category
filter does not have to call an app-facing route with a staff JWT that has no profile row.

### Frontend
1. Types: `FeedbackMessageDto` (note `deleted`, `net_votes`, `up_votes`, `down_votes`,
   `staff_response`, `completed_at`), `FeedbackCategoryDto`, `FeedbackFeedStatusDto`.
2. `src/api/feedbackFeed.ts` (or extend `admin.ts`) with the six + one calls.
3. `src/pages/RoadmapPage.tsx` — "Top requests" panel from `/top` at the head of the page;
   list with type + status filters and an `include_removed` toggle; per-card expand showing
   the author's message with the staff response beneath it (never in place of it — the
   backend does no substitution); status select, response textarea (1000-char counter,
   empty = clear), remove with confirm; removed rows visibly struck through.
4. Route `/roadmap`, sidebar entry under **Product** (new section) with icon `Rocket` and
   a badge for new/unreviewed requests (Phase 5).
5. `labels.ts` — roadmap status + category label/tone maps.
6. Rename the existing nav label "Feedback" → "App Feedback" so the two are distinguishable.

**Effort:** ~1–1.5 days frontend, ~1 hour backend.

---

## Phase 3 — Badges

### Backend
`AdminBadgesController`, all `MANAGE_BADGES`:

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/admin/badges` | `AdminBadgeDto[]` = `{ badge, holders }`, retired included |
| POST | `/api/v1/admin/badges/{badgeId}` | `BadgeUpsertRequest` → `BadgeDto` |
| PUT | `/api/v1/admin/badges/{badgeId}` | update |
| DELETE | `/api/v1/admin/badges/{badgeId}` | only while `holders == 0` |
| POST | `/api/v1/admin/badges/{badgeId}/holders/{userId}` | grant, idempotent, records `granted_by` |
| DELETE | `/api/v1/admin/badges/{badgeId}/holders/{userId}` | revoke, idempotent |
| GET | `/api/v1/admin/badges/holders/{userId}` | `BadgeGrantDto[]` — the only place `granted_by` is exposed |

**To add — badge artwork upload.** `BadgeUpsertRequest.unlockedKey` / `lockedKey` are R2
object keys and are explicitly rejected if they look like URLs, but nothing presigns an
upload into the shared app-assets bucket (`StorageBucket`'s shared-assets entry). Add
`GET /api/storage/badges/{badgeId}` (or a `?variant=unlocked|locked` param) returning the
existing `UploadUrlResponse`, gated on `MANAGE_BADGES` — without it a badge can only be
created after someone uploads the SVGs to R2 by hand.

### Frontend
1. `src/pages/BadgesPage.tsx` — catalogue grid (unlocked/locked artwork, title, description,
   `award_trigger`, earnable window, holder count, retired styling for `available: false`);
   create/edit drawer with the two artwork uploads (presign → PUT → submit the returned key),
   delete disabled with a tooltip when `holders > 0`.
2. Grant flow — user picker backed by the existing `GET /api/v1/profile/search?q=` (no JWT
   principal required, so a staff token works), then grant/revoke; a per-user panel using
   `/holders/{userId}` that distinguishes automatic awards (`granted_by == null`) from
   hand-grants.
3. Route `/badges`, sidebar under **Community**, icon `Award`.
4. Reminder: staff ids have no profile row — the grant target is an **app profile** UUID,
   the granter is a **staff auth** UUID. Do not render the granter with profile components.

**Effort:** ~1.5 days frontend, ~2 hours backend.

---

## Phase 4 — Business account verification (backend first)

Only `active_status = 'active'` **and** `verification_status = 'verified'` businesses reach
the app. Everything else — pending submissions, rejections, suspensions — is invisible and
unmanageable outside the SQL editor.

### Backend
1. New capability `VERIFY_BUSINESSES` in `Capability.java`; grant to `owner` and
   `senior_admin` in `AdminRole` (same reasoning as `APPROVE_EVENTS` — publishing decision,
   not moderation).
2. `AdminBusinessController` at `/api/v1/admin/businesses`:
   - `GET ?verification_status&active_status&cursor&size` — keyset page, oldest first.
   - `GET /counts` — `{ pending: n }`.
   - `GET /{businessId}` — full detail regardless of status.
   - `POST /{businessId}/verify` → sets `verified`, stamps `verified_at`.
   - `POST /{businessId}/reject` — body `{ reason }`.
   - `PATCH /{businessId}/active-status` — suspend / reactivate.
3. Service methods on the `business` module's public interface (keep entities in
   `internal/`; the admin module must not reach past the named interface). Add an
   `AdminBusinessDto` in the business module's `dto` package that carries the status fields
   `BusinessDto` deliberately hides.
4. Tests: web slice for authz (each role × each route) + service unit tests for the status
   transitions, per `TESTING_ROADMAP.md` conventions.

### Frontend
`src/pages/BusinessesPage.tsx` — same shape as the events queue (status tabs, cursor paging,
detail panel with logo/location/type, Verify / Reject-with-reason / Suspend). Route
`/businesses`, sidebar under **Moderation**, icon `Store`.

**Effort:** ~1.5 days backend (incl. tests), ~1 day frontend.

---

## Phase 5 — Overview, sidebar, and permissions catch-up

### Backend
1. `OverviewDto.BadgeCountsDto` — add `pendingEvents` (pending map-event submissions) and
   `newRoadmapRequests` (feedback-feed messages still in the initial status), plus
   `pendingBusinesses` once Phase 4 lands. `AdminOverviewService` gains the two counts;
   they must be cheap (indexed count queries) since the sidebar polls this on every page.
2. Consider adding `contentMix` rows for map events and feedback-feed messages so the donut
   reflects the current product.
3. `TRANSFER_OWNERSHIP` — either implement `POST /api/v1/admin/team/{memberId}/transfer-ownership`
   (owner only, atomic role swap) or drop the capability. Leaving a documented-but-absent
   capability in the matrix is the current state and it misleads the UI.

### Frontend
1. `ModeratorsPage` permission matrix — add `APPROVE_EVENTS`, `MANAGE_ROADMAP`,
   `MANAGE_BADGES` (and `VERIFY_BUSINESSES` after Phase 4) with the correct per-role ticks
   from `AdminRole.java`. This table is hand-maintained; it is wrong today.
2. Sidebar — new sections and badges for the pages added above; keep hiding items the
   signed-in role cannot use.
3. Overview — surface pending events / roadmap requests as stat cards alongside the existing
   four.
4. Refresh `PROGRESS.md`: the repo *is* under git now, and the invite happy path / end-to-end
   authed pass are still open items from July.

**Effort:** ~0.5 day each side.

---

## Phase 6 — Optional / later

- **Event contests oversight.** Since `ContestClock` was removed (2026-09-08) a contest only
  closes when an organizer taps FINISH or the event finishes. A contest whose organizer
  abandons it stays `open` forever. A read-only admin list of open contests, plus a
  force-finish that calls the existing `ContestFinalizer.finalizeLocked(...)`, would be the
  safety valve. Backend + frontend.
- **Push notification broadcast.** An admin-composed announcement to a segment, via the
  existing `PushDispatcher` / `FcmSender`. Meaningful product surface, not a gap — needs
  its own design (targeting, rate limits, opt-out respect).
- **Reputation admin.** Manual adjustment / audit of reputation events. Only if abuse shows up.
- **Analytics** — DAU/MAU/session/churn, still blocked on activity tracking not existing.
- **Search** across tickets and feedback; canned replies.
- **TanStack Query** if optimistic updates keep multiplying across the new pages.

---

## Suggested order

1. **Phase 1** (map events) — blocking real users today, zero backend work.
2. **Phase 2** (roadmap) — one small backend addition, high owner value.
3. **Phase 5** frontend half (permission matrix) — an hour, and it is currently wrong.
4. **Phase 3** (badges) — needs the storage endpoint first.
5. **Phase 4** (businesses) — largest backend piece.
6. **Phase 5** backend half, then **Phase 6** as needed.

## Conventions to hold to

**Backend** (from [`CLAUDE.md`](CLAUDE.md)): controllers stay thin — `jwt.getSubject()`,
`access.require(userId, Capability.X)`, delegate, return a DTO record. `/api/v1/<module>/…`.
Nothing in `internal/` becomes `public` to satisfy the admin module; add to the module's
named interface instead. Any new column needs a Supabase migration — `ddl-auto: validate`
crashes on divergence.

**Frontend** (from `Tweakd-Dashboard/PROGRESS.md`): DTOs in `types.ts` mirror the wire
exactly in snake_case (`notification.payload` keys stay camelCase); `CursorPage<T> =
{items, next_cursor}`; enum labels/tones live in `labels.ts`, never inline; `shadow-soft` /
`shadow-pop`, never `shadow-sm/lg`; `text-bg` on ink buttons, never `text-white`; remote
images through `safeUrl` + `referrerPolicy="no-referrer"`; staff refs (`StaffRefDto`) are
never linked to profile UI. All permission gating is UX-only — the backend re-authorizes
every request.

---

## 7. What shipped (2026-09-12)

### Backend — 795 tests pass (`./mvnw test -Dtest='!*IT'`; +47 new)

| Area | Change |
|---|---|
| Capabilities | `VERIFY_BUSINESSES` and `MANAGE_CONTESTS` added; `TRANSFER_OWNERSHIP` is no longer a TODO |
| Businesses | `AdminBusinessesController` + the admin section of `BusinessService`: list / counts / detail / verify / reject / active-status. New DTOs (`AdminBusinessDto`, `AdminBusinessSummaryDto`, `AdminBusinessPageDto`), `BusinessCursor`, two exceptions, migration for `rejection_reason` / `reviewed_by` / `reviewed_at` |
| Contests | `AdminContestsController` + `listContestsForReview` / `countOpenContests` / `finishContestAsAdmin` on `MapEventContestsService`, `AdminContestDto` |
| Team | `POST /team/{id}/transfer-ownership`, `AdminTeamService.transferOwnership` (both rows `FOR UPDATE` in uuid order, demote-then-promote), `admin_team_members_single_owner_idx` |
| Roadmap | `GET /admin/feedback-feed/types` (the category filter no longer has to call an app route with a profile-less staff token) |
| Badges | `GET /admin/badges/artwork-base-url`, `StorageService.publicBaseUrl` — artwork stays out-of-band, per the owner's call |
| Overview | `BadgeCountsDto` gained `pendingEvents`, `newRoadmapRequests`, `pendingBusinesses` (one statement, six branches); content mix gained map events and roadmap requests |
| Indexes | Partial indexes for the pending-business and un-triaged-roadmap counts; the pending-event count already had `car_events_map_idx` |

New tests: `AdminBusinessesControllerWebTest` (12), `BusinessServiceImplReviewTest` (14),
`AdminTeamServiceOwnershipTest` (5), `AdminContestsControllerWebTest` (8), six admin-oversight
cases in `MapEventContestsServiceImplTest`, and two more in `AdminRoleCapabilityTest`.

### Frontend — `npm run build` and `oxlint` clean

Five new pages (`/events`, `/businesses`, `/roadmap`, `/badges`, `/contests`), a shared
`src/lib/capabilities.ts` mirroring `AdminRole.java`, a `useMe()` hook, capability-gated
navigation, the permission matrix generated from the shared table rather than a drifted literal,
the ownership-transfer control, an Overview "waiting on you" strip, and route-level code splitting
(main chunk 652 kB → 324 kB). Details in `Tweakd-Dashboard/PROGRESS.md`.

### Not done, deliberately

- **Push broadcast** and **reputation admin** — deferred by the owner; they are features with their
  own design questions (targeting, rate limits, opt-out; abuse signals), not gaps.
- **Business create/edit** — the owner chose verify/suspend only. The module stays read-only for
  everything else until business login is designed.
- **Badge artwork upload** — the owner chose to keep uploads out of band; the form takes an R2 key
  and previews it.
- **End-to-end verification.** Nothing here has been exercised against a running backend with a
  real staff session: the backend suite passes and the dashboard compiles and lints, which is not
  the same as "used". That pass, plus applying the two migrations, is the next step.
