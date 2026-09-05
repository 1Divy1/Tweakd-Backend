# badges module

Unlockable badges: the admin-curated catalogue of what can be earned (`badges`), and which user has
earned what (`user_badges`).

A badge is a **current-state fact** — you hold it or you don't. That one sentence explains most of
the design decisions below, and every place this module differs from `reputation`.

## Badges vs reputation

They look similar and behave differently. Both are earned by doing things; only one is a ledger.

| | reputation | badges |
|---|---|---|
| Shape | running total + itemised history | a set of things you hold |
| Taking one back | soft: `revoked_at`, row kept, score subtracted | hard: the row is deleted |
| After taking it back | frees the source to be re-earned | badge is immediately re-earnable |
| Retiring the definition | `is_active = false` on the reason | `is_available = false` on the badge |
| Paging | keyset — history grows without bound | none — a user holds a handful |

They are independent: an achievement may award reputation, a badge, both, or neither. Awarding both
is two calls, in the same transaction as the achievement.

## Artwork lives in R2, keys live in the database

`badges.unlocked_badge_url` and `badges.locked_badge_url` hold **R2 object keys**, not URLs:

```
badges/pioneer/badge-unlocked.svg
```

The full public URL is built at read time from the configured `app-assets` bucket, through
`StorageService.publicUrl(StorageBucket.ASSETS, key)` — the same treatment avatars and post images
get. Moving the bucket or its domain is then a config change rather than a data migration, and no
caller outside this module ever sees a key.

A CHECK constraint refuses a value starting with `http`, and `BadgeUpsertRequest` rejects one at the
edge, because the failure mode is silent: a full URL would be prefixed again and 404 in the app with
nothing in a log.

`locked_badge_url` is optional. `null` means the badge has no locked variant and the client greys
out the unlocked artwork itself.

## Where badges are read — and why the dependency points this way

A user's earned badges are **embedded in the profile response**. `ProfileDto` and
`PublicProfileDto` each carry a `badges` array, so the profile screen paints its badge row in the
same round trip as the header — the way Instagram highlights sit under the bio, rather than popping
in a beat later.

That decides the module graph. `profile` imports `badges`, so **`badges` must not import
`profile`** — two modules importing each other is a cycle and `ModularityTests` fails the build on
it. Concretely, this module gives up two things to stay importable:

- **No username-keyed read.** There is no `GET /badges/users/{username}`; another user's badges come
  with their profile. The username-keyed *refetch* lives in `profile`
  (`GET /api/v1/profile/by-username/{username}/badges`), which is the module that owns username
  resolution anyway.
- **No profile existence check on award.** The foreign key still refuses an unlock for a user who
  isn't there; it surfaces as a constraint error rather than a tidy 404. Callers award from inside
  the transaction that just handled that user, so there is nothing to look up.

## Awarding

Two ways in, and no way for a user to award themselves — the module's own controller has no award
path (its one write only acknowledges an animation; see the API section).

**Automatically**, from the module that witnessed the achievement:

```java
badgeService.award(userId, Badges.PIONEER);
```

Call it inside the achievement's own transaction, so the badge and the thing that earned it commit
together.

**Idempotent.** A user who already holds the badge gets back the row they already have, with
`earnedAt` unchanged — a retried listener, a replayed job and a double-tapped action all collapse
onto the first unlock. The guarantee is the unique index on `(user_id, badge_id)`, not the
service's pre-check, so it holds under concurrency; the pre-check is just the fast path that turns
a would-be constraint violation into a 200. **Callers need no dedup guard of their own.**

Because a badge is a statement about the present, "already earned" is success rather than a
conflict. There is no second award to record and nothing for a caller to handle differently.

**By hand**, from the dashboard, for badges that are a judgement call rather than something a rule
can detect — `pioneer` is one. Those go through `grant(userId, badgeId, staffId)` and stamp the
granting staff member on `user_badges.granted_by`. `null` there means the backend awarded it
automatically, which is how the two are told apart afterwards.

The granter is **staff-only**. `UserBadgeDto` — the app-facing shape — has no field for it, and the
only read that exposes it is the dashboard's `GET /admin/badges/holders/{userId}`, which returns
`BadgeGrantDto`. A badge on a profile is the user's achievement; "granted by X" beside it would read
as a favour rather than something earned. The column exists so a hand-grant can be traced later, and
that is all it is for.

## Retiring vs deleting

Withdrawing a badge from the catalogue is `available = false`. It stops being awardable and drops
out of the catalogue; every profile already holding it is untouched, and those users keep seeing it.

Deleting is only possible while **nobody** holds it. The `user_badges` foreign key is
`ON DELETE RESTRICT`, so the database would refuse it anyway — the service checks first so the
dashboard gets a 409 that says how many users hold it, rather than a 500.

## API

Public interface: `BadgeService`. Badge codes the backend awards by name are constants on `Badges`.

### App endpoints — `/api/v1/badges` (authenticated)

| Method | Path | Returns |
|---|---|---|
| GET | `/me` | the caller's own earned badges, newest unlock first |
| GET | `/me/locked` | every available badge the caller has **not** earned, oldest first |
| GET | `/me/pending-celebration` | earned badges whose unlock animation the app still owes, oldest first |
| POST | `/me/pending-celebration/{badgeId}` | acknowledge that animation played → `{"celebrated": <bool>}` |
| GET | `/catalogue` | every badge that can currently be unlocked, oldest first |

All reads bar one. The `POST` is the only write, and it cannot award or change a badge — it flips
`user_badges.granted_in_app` to `true` for a badge the caller already holds, so its one-time
Duolingo-style unlock animation does not replay. Idempotent: a repeat, or a call for a badge the
caller doesn't hold, returns `{"celebrated": false}`. Badges earned before this feature shipped
were backfilled to `granted_in_app = true`, so they never retro-animate.

**On launch the app does not call `GET /me/pending-celebration` at all.** That list is folded into
the first page of the global feed (`GET /api/v1/feed/global` with no cursor) as
`pending_badge_celebrations` — the request the app already fires on startup. The `feed` module
composes it via `BadgeService.listPendingCelebrations`; paged feed requests carry an empty list, so
scrolling never replays an animation. The standalone `GET /me/pending-celebration` stays for a
mid-session refetch. Either way, the app plays each animation and then `POST`s the id back to the
badges endpoint above.

Plus, served by the `profile` module:

| Method | Path | Returns |
|---|---|---|
| GET | `/api/v1/profile/me` · `/by-username/{username}` | the profile, with its `badges` array |
| GET | `/api/v1/profile/by-username/{username}/badges` | just the badge row, for refetching it alone |

Nothing is paginated. A user holds a handful of badges and the catalogue is curated, so all of it
fits in one response.

**The locked list is the caller's own only.** There is no username-keyed equivalent: what someone
else has yet to achieve is not the viewer's business, and the full catalogue is already public. It
carries `locked_url` and the badge's `description` — the "here is what to do to unlock this" text —
and excludes retired badges, since something that can no longer be earned is not a goal.

An earned list **includes badges since retired** from the catalogue. Earning one is a fact about the
user; taking it off their profile because the badge is no longer offered would be rewriting history.
So a badge can appear in someone's earned list and be absent from both the catalogue and everyone
else's locked list.

### Dashboard endpoints — `/api/v1/admin/badges` (ROLE_ADMIN + `MANAGE_BADGES`)

| Method | Path | Does |
|---|---|---|
| GET | `/` | the whole catalogue with holder counts, retired ones included |
| POST | `/{badgeId}` | create — 409 if the code is taken |
| PUT | `/{badgeId}` | edit; `available: false` retires |
| DELETE | `/{badgeId}` | delete — 409 while anyone holds it |
| GET | `/holders/{userId}` | what one user holds, **with `granted_by`** — the only read that exposes it |
| POST | `/{badgeId}/holders/{userId}` | grant by hand (idempotent) |
| DELETE | `/{badgeId}/holders/{userId}` | take it back (idempotent) |

`MANAGE_BADGES` is owner and senior admin only. Putting a badge on someone's profile is a
recognition decision, not a moderation one — the same reasoning that keeps `APPROVE_EVENTS` and
`MANAGE_ROADMAP` off the content-moderator role.

The badge code sits in the path on create as well as update. It is not editable afterwards (every
unlock references it), so it is chosen once, and addressing the resource by it makes that visible.

## Entities

| Entity | Table | Notes |
|---|---|---|
| `BadgeEntity` | `badges` | text PK — the stable code. `unlockedKey` / `lockedKey` map the two url columns |
| `UserBadgeEntity` | `user_badges` | uuid PK, `@ManyToOne` to the badge (LAZY, always `join fetch`ed). `grantedInApp` is the unlock-animation latch |

## Schema dependencies

- `user_badges_user_badge_uq` — unique on `(user_id, badge_id)`. **Load-bearing**: it is what makes
  `award` exactly-once. It also serves the only per-user read this module has.
- `user_badges_badge_id_idx` — indexes the referencing side of the badge FK, which Postgres does not
  do for you. Without it, `ON DELETE RESTRICT` checks, `ON UPDATE CASCADE` rewrites and the
  dashboard's holder counts all scan the table.
- The locked list is an anti-join (`not exists`) rather than fetch-both-and-subtract-in-Java: one
  query instead of two, and the `not exists` is served by the unique index above.
- `badges_urls_are_keys` — CHECK: neither url column may start with `http`.
- `user_badges.granted_in_app` — `boolean NOT NULL DEFAULT false`. The unlock-animation latch. No
  index of its own: "the caller's un-celebrated badges" filters on `user_id` through the unique
  index, then this flag in memory over the handful that returns.
- `user_badges.badge_id` → `badges.id`, `ON DELETE RESTRICT ON UPDATE CASCADE`.
- `user_badges.user_id` → `profiles.id`, `ON DELETE CASCADE` — deleting a profile takes its badges.

No triggers. Both tables are RLS-enabled with no policies and no grants to `anon` / `authenticated`:
badges reach the app only through this module's endpoints, which is what lets it filter retired
badges and resolve the artwork URLs.

## Cross-module dependencies

`storage` (public URLs) and `shared` (exceptions) — **deliberately not `profile`**, so that
`profile` can depend on this module instead and embed badges in its response. `admin` depends on it
for the dashboard's badge administration.
