# reputation module

Community reputation: the itemised, dated record of everything a user has done to earn their
score. Owns `reputation_score_reason_options` (the catalogue) and `reputation_score_history` (the
timeline).

The point of the feature is that the number is **not fakeable** — it takes time and turning up.
So the timeline is the product, not the total: anyone deciding whether to trust a seller can read
what they actually did and when.

## Ownership split

| Thing | Owned by | Why |
|---|---|---|
| `profiles.reputation_score` | **profile** | It is a column on `profiles`; that module owns the table |
| `reputation_score_history` | **reputation** | The itemised timeline behind the number |
| `reputation_score_reason_options` | **reputation** | The catalogue the timeline references |

The seam is `ProfileService.applyReputationDelta(userId, delta)`, which moves the score under a
pessimistic write lock and reports the before/after pair. No module writes another's table.

## Consistency, and why there is no trigger

An award moves the score and writes its history row **in one transaction**:

1. `ProfileService.applyReputationDelta` takes the profile row `FOR UPDATE`, adds the delta, clamps
   at zero, returns `(previousScore, newScore)`.
2. The history row is written from exactly that pair.

So `previous_score + score_gain = new_score` holds on every row. A database trigger firing
independently could not guarantee that — it would compute its own before/after, and two concurrent
awards could record the same `previous_score`. The lock is what serialises them.

**Callers must invoke `award(...)` inside the achievement's own transaction**, so the points and the
thing that earned them commit together. An accepted forum answer that rolls back must not leave
points behind.

## Awarding: get the timing right first

Most of what looks like "we need to undo an award" is really an award that fired too early. Points
for organizing an event belong on the event *finishing*, not on it being created. Points for
attending belong on *check-in*, not on RSVP. An award that only ever fires on something that has
already happened never needs revoking.

| Reason | Category | Points | Award from | Source |
|---|---|---|---|---|
| `car_event_organized` | events | 50 | `markFinished()` — **not** `createEvent()` | `car_event` |
| `event_car_showcased` | events | 20 | `markFinished()`, per approved participant | `car_event` |
| `event_attended` | events | 10 | check-in, or `markFinished()` for confirmed attendees | `car_event` |
| `contest_first_place` | contests | 50 | contest results being finalised | `contest` |
| `contest_second_place` | contests | 40 | ditto | `contest` |
| `contest_third_place` | contests | 30 | ditto | `contest` |
| `first_mod` | garage | 20 | first modification saved on a car | `car` (**not** the mod — see below) |
| `annual_member_anniversary` | trust | 100 | scheduled job, guarded by `hasEarnedSince` | none |
| `event_no_show` | moderation | −50 | `markFinished()`, for attendees who never checked in | `car_event` |

Penalties are just negative `points` in the `moderation` category. The resulting score is clamped at
zero, and the history row stores the **clamped** delta, so `previous + gain = new` still holds.

## Idempotency — the source link

Award with a `ReputationSource` wherever there is a row to point at:

```java
reputationService.award(
        userId,
        ReputationReasons.EVENT_ATTENDED,
        new ReputationSource(ReputationSourceType.CAR_EVENT, eventId, event.getTitle()));
```

That does two things:

- **Deep-linking.** The timeline entry reads `+10 · Attended a car event · Cluj Auto Show` and can
  navigate to the event.
- **Exactly-once.** A partial unique index on `(user_id, reason, source_type, source_id) where
  source_id is not null and revoked_at is null` means a retried listener, a double-tapped check-in
  or a replayed job all get the entry already on file back, with the score untouched. **Callers need
  no guard of their own.** The guarantee is in the database, so it holds under concurrency; the
  service's pre-check is just a fast path.

What the source is scoped to *is* the dedup key, so choose it deliberately. `first_mod` is scoped to
the **car**, not the modification — that is what makes it the *first* mod, because the second
modification on that car finds the award already there.

Reasons with no source (`annual_member_anniversary`) are not covered by the index. Guard those with
`hasEarnedSince`:

```java
if (!reputationService.hasEarnedSince(userId, ReputationReasons.ANNUAL_MEMBER_ANNIVERSARY,
        Instant.now().minus(365, ChronoUnit.DAYS))) {
    reputationService.award(userId, ReputationReasons.ANNUAL_MEMBER_ANNIVERSARY);
}
```

That is a read-then-write, so it is not race-proof. Fine for the scheduled job it exists for;
anything awarded from a request path should carry a source instead.

## Revocation

For facts that genuinely change *after* the points were paid — an event cancelled after it finished,
a corrected contest placement, an appealed penalty:

```java
reputationService.revoke(organizerId, ReputationReasons.CAR_EVENT_ORGANIZED,
        ReputationSource.of(ReputationSourceType.CAR_EVENT, eventId),
        "Event was cancelled");
```

- Subtracts **`score_gain`** — the delta the entry actually applied — so a revocation is exactly
  symmetric to its award, even for a penalty that had hit the zero floor.
- Stamps `revoked_at` / `revoked_reason`; the row is **kept, never deleted**.
- Takes the history row `FOR UPDATE`, because reading its delta and subtracting it is a
  read-modify-write across two tables.
- Idempotent: revoking something never awarded, or already revoked, returns `false`.
- Frees the source to be earned again (the unique index is partial on `revoked_at is null`), so an
  un-cancelled event re-awards cleanly.

### Who sees a revocation

**Only the owner.** `/me/history` includes revoked entries with their reason, because a score that
silently drops 50 points is worse than one that explains itself. `/users/{username}/history` omits
them entirely — not flagged, *absent*. Publishing "this award was reverted" to strangers would turn
the timeline into a shaming mechanic.

The split is structural, not a flag: `getHistory(UUID, …)` is the privileged read (reached from the
caller's own JWT subject, or by staff holding the id) and `getHistoryByUsername(String, …)` is the
public one, with no parameter that could ask for the revoked view.

Summary figures — count, per-category split, last-earned — are **live-only for everyone**, so the
breakdown always adds up to the score and a revocation is invisible in aggregate.

## Why the source link is not a foreign key

`source_id` deliberately has no FK to `car_events`:

- An FK constrains only DELETEs, so it would not block reshaping the events feature — but deletes
  are exactly the problem. `deleteEvent` and `deleteEventAsAdmin` both exist. `ON DELETE RESTRICT`
  would make an event undeletable once anyone earned points at it; `ON DELETE CASCADE` would erase
  reputation people actually earned, which is the one thing this table exists to prevent.
- Sources are polymorphic: four reasons point at `car_events`, three at contests (no table yet),
  `first_mod` at cars, the anniversary at nothing. A real FK means one more nullable column and one
  more migration per feature, forever.

Instead, `source_label` is **snapshotted at award time**. So this module reads only its own tables —
no join, no cross-module call, no dependency on the current shape of events — and an entry stays
readable after its source is renamed or deleted. The trade-off: a later rename does not propagate.
For an append-only ledger, showing what a thing was called when the points were earned is arguably
the more honest answer anyway.

## Public API

### `ReputationService`

| Method | Description |
|---|---|
| `award(userId, reasonId)` | Catalogue default points, no source. For achievements that genuinely have none |
| `award(userId, reasonId, source)` | **Preferred.** Idempotent per source, deep-linkable |
| `award(userId, reasonId, points)` | Explicit delta — occasion-dependent worth. Zero refused |
| `award(userId, reasonId, points, source)` | Both |
| `revoke(userId, reasonId, source, reason)` | Takes an award back, symmetrically. Returns `false` if there was nothing to take |
| `hasEarnedSince(userId, reasonId, since)` | Guard for time-boxed, sourceless reasons |
| `getSummary(userId)` / `getSummaryByUsername(username)` | The profile reputation block |
| `getHistory(userId, cursor, size)` | Timeline **including** revoked entries — privileged |
| `getHistoryByUsername(username, cursor, size)` | Timeline **excluding** revoked entries — public |
| `listReasons()` | The active catalogue, for the "how reputation works" sheet |

`ReputationReasons` and `ReputationSourceType` hold the codes as constants — deliberately not enums,
so the catalogue can grow a row without a deploy. An unknown code still fails loudly at award time.

### DTOs (`@NamedInterface("dto")`)

| Type | Fields |
|---|---|
| `ReputationSummaryDto` | userId, score, achievements, pointsByCategory, lastEarnedAt |
| `ReputationEntryDto` | id, reasonId, label, category, scoreGain, previousScore, newScore, sourceType, sourceId, sourceLabel, revokedAt, revokedReason, createdAt |
| `ReputationHistoryPageDto` | items, nextCursor |
| `ReputationReasonDto` | id, label, points, category, isRepeatable |
| `ReputationSource` | type, id, label — the **input** record for awarding |

There is no `description` on a reason: the `reason` column already reads as a full sentence, so a
second prose column was redundant.

### Exceptions

| Exception | HTTP | Trigger |
|---|---|---|
| `ReputationReasonNotFoundException` | 404 | Unknown or retired reason code |
| `InvalidReputationAwardException` | 400 | Zero points, or a revocation with no source |
| `InvalidReputationCursorException` | 400 | Undecodable pagination cursor |

A duplicate award is **not** an exception — it returns the entry already on file. One rule for both
one-time reasons and source-scoped ones, and callers can fire-and-forget from listeners.

## REST endpoints

Base path `/api/v1/reputation`. **All reads.** Reputation is never granted over HTTP, so there is no
endpoint a client could call to inflate its own score — awarding is SPI-only, from the module that
witnessed the achievement.

| Method | Path | Description |
|---|---|---|
| GET | `/me` | The caller's own reputation block |
| GET | `/me/history?cursor=&size=` | Own timeline, **including** revoked entries |
| GET | `/users/{username}` | Any user's reputation block |
| GET | `/users/{username}/history?cursor=&size=` | Public timeline, revoked entries **omitted** |
| GET | `/reasons` | The active catalogue |

Pagination is keyset, not offset: `size` defaults to 20 and is clamped to 50, `size + 1` rows are
fetched to detect `has_more` without a count query, and `next_cursor` is an opaque URL-safe Base64
token (`<epochSecond>:<nano>:<uuid>`). `null` means last page. Ordering is `(created_at desc,
id desc)`, matching the index exactly, so pages are index seeks rather than a sort per request.

## Entities

### `ReputationReasonEntity` → `reputation_score_reason_options`

| Column | Type | Notes |
|---|---|---|
| id | text | PK. The code stored on every history row |
| reason | text | Display sentence; mapped to `label` in Java |
| points | int | Default delta. Negative for penalties. DB refuses zero |
| category | text | DB check: events, contests, community, garage, trust, marketplace, moderation |
| is_repeatable | boolean | `false` = once ever; a second award returns the existing entry |
| is_active | boolean | Retire a reason with `false` — **never** DELETE (the FK refuses it anyway) |
| created_at | timestamptz | DB-managed |

### `ReputationScoreHistoryEntity` → `reputation_score_history`

| Column | Type | Notes |
|---|---|---|
| id | uuid | PK, set **client-side** (`UUID.randomUUID()`), matching the codebase convention |
| user_id | uuid | Flat reference to `profiles.id` — no association across the module boundary |
| reason | text | FK → `reputation_score_reason_options.id`, `ON UPDATE CASCADE ON DELETE RESTRICT` |
| score_gain | int | The delta **actually applied**, already clamped |
| previous_score / new_score | int | Stored, not derived — the entry shows the score as it stood that day |
| source_type / source_id | text / uuid | The thing that earned it. **No FK** — see above |
| source_label | text | Snapshot of the source's name at award time |
| revoked_at / revoked_reason | timestamptz / text | Set when taken back. Owner-visible only |
| created_at | timestamptz | DB-managed (`DEFAULT now()`), hence `saveAndFlush` + `refresh` |

`created_at` and the client-side id together are why awarding does `saveAndFlush()` then
`entityManager.refresh()`: with the id pre-set, `save()` merges rather than persists, and the
DB-managed timestamp has to be read back.

## Supabase

| Migration | Status |
|---|---|
| `20260903120000_reputation_score_reason_options.sql` | **Applied.** Rewritten to match what was actually run — no `description` column, 9 seeded reasons |
| `20260903140000_reputation_source_and_revocation.sql` | **Pending — apply before deploying** |

Until the second is applied, `ddl-auto: validate` will refuse to start the app. The Testcontainers
ITs pass via a clearly-marked `PENDING MIGRATION` block appended to
`src/test/resources/db/schema.sql`; re-run `./scripts/dump-schema.sh` after applying and the block
disappears.

`reputation_score_reason_options` follows the reference-table convention: RLS enabled with zero
policies (deny-all to `anon`/`authenticated`; the backend reads as table owner).

## Cross-module dependencies

Depends on `profile` (`ProfileService` — the score column, username resolution) and
`shared/exception`. Nothing depends on `reputation` yet: the award calls into `mapevents`, `garage`
and contests are deliberately **not** wired, so this module needs no rework as those features
settle. The table above says where each call belongs when you are ready.
