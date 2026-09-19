# mapevents module

Owns **car events on the map** — the pins users tap to find meets near them, the floating widget
behind a pin, the full event page (organizers, the car line-up, attendees), and the **contests**
run inside an event.

Users create events from the app; **an admin must approve one before it appears on the map**.

## Naming

The module is "map events" (it is a map feature) and lives at `/api/v1/map-events`; the Supabase
tables it owns are named `car_event*`. Types here carry the `MapEvent` prefix and name their table
explicitly. Tables owned: `car_events`, `car_event_categories`, `car_event_organizers`,
`car_event_organizer_rules`, `car_event_attendees_list`, `car_event_participants`, `event_car_meet`,
`car_event_contest_categories`, `car_event_contests`, `car_event_contest_entries`,
`car_event_contest_votes`, plus the three status reference tables. Contest types carry the
`Contest` prefix rather than `MapEventContest` — they only exist inside an event, so the longer
name buys nothing.

## Two independent states

| Column | What it means | Who moves it |
|---|---|---|
| `approval_status` | the admin gate: `pending` → `accepted` / `rejected` | admins only |
| `status` | the event's own life: `upcoming` / `live` / `previous` / `hidden` / `canceled` | organizers |

**Nothing sweeps `status` automatically.** Automatic `upcoming → live → previous` transitions were
deliberately deferred by the owner, so there is no `@Scheduled` job here — and the same call was
made for contests: they open and close only when an organizer taps (see § Contests). Two consequences the code
depends on:

- an organizer marks an event finished (`previous`) or cancels it (`canceled`) by hand, and
- **read paths take the clock into account as well as the stored status.** The map query filters
  `coalesce(ends_at, starts_at + interval '24 hours') >= now()`, so an event nobody marked finished
  drops off the map anyway. `MapEventEntity.hasFinished(now)` applies the same rule to RSVP and
  registration.

Approval also **locks** an event: once accepted, organizers can no longer edit it (only cancel), so
an event cannot be approved as one thing and quietly become another. Editing a *rejected* event
clears its `rejection_reason` and resubmits it as `pending`.

## Who may do what

| Action | Creator | Co-organizer (individual) | Business co-organizer | Anyone |
|---|:--:|:--:|:--:|:--:|
| Edit (while pending/rejected) | ✅ | ✅ | ❌ | ❌ |
| Attach / replace cover | ✅ | ✅ | ❌ | ❌ |
| Replace rules (while pending/rejected) | ✅ | ✅ | ❌ | ❌ |
| Cancel, mark finished | ✅ | ✅ | ❌ | ❌ |
| Accept / reject entered cars | ✅ | ✅ | ❌ | ❌ |
| Add / remove organizers | ✅ | ❌ | ❌ | ❌ |
| Delete the event | ✅ | ❌ | ❌ | ❌ |
| RSVP, enter own car | ✅ | ✅ | ❌ | ✅ |

`car_events.created_by` records the creator immutably; the creator *also* holds a
`car_event_organizers` row with `role = 'creator'` (partial unique index: one per event), so the
roster is complete on its own. Everyone added later is `role = 'organizer'`.

**Business co-organizers are credit only.** `business_accounts` has no link to `auth.users` yet, so
nobody can act as a business; they are displayed and linked on the event page and hold no
permissions. Every permission check looks at *individual* organizers.

## Attendees vs participants — different things

- **Attendees** (`car_event_attendees_list`) are **spectators**. Open RSVP — `attending` or
  `interested` — with no organizer approval, which is why there is no pending state on that table.
  RSVP stays open **while the event is running** and closes only once it has finished or been
  cancelled.
- **Participants** (`car_event_participants`) are **cars**, entered by their owner. Only the owner
  may enter a car. Registration closes at `event_car_meet.registration_deadline`. With
  `requires_participant_approval` the row starts `pending` and an organizer accepts/rejects it;
  otherwise it is `accepted` outright. Rejecting requires a `rejection_reason` (same rule as
  rejecting an event), cleared if the car is later accepted. `listParticipants` hides
  `pending`/`rejected` rows from everyone but an organizer, so the owner reads their own standing —
  including the reason — through `listMyParticipants` instead.

`car_events.max_participant_capacity` is an optional cap (`null` = no limit) on the **accepted**
line-up. `registerCar` rejects a brand-new entry once `attending_cars_count` reaches the cap
(`EventClosedException`); re-sending an already-registered car is a no-op and is never blocked by
it. A `pending` entry submitted before the cap was hit is left alone — the cap does not retroactively
reject it, and an organizer may still accept or reject it — so `attending_cars_count` can briefly
exceed the cap by one if an organizer accepts a pending car after the line-up filled up.

Both counts (`attendees_count`, `attending_cars_count`) are **trigger-owned**
(`trg_update_car_event_attendees_count`, `trg_update_car_event_attending_cars_count`); this module
never writes a count column. After an RSVP change the row is re-read (`entityManager.refresh`) so
the response shows the new total rather than the stale one in the session.

## Subcategories

`car_events.event_type` → `car_event_categories`. Only `car_meet` exists today, and each category
gets its own detail table — the car meet's is `event_car_meet` (`registration_deadline`).
`is_available` gates a category on the create screen without invalidating existing events.
`CarMeetDetailsDto` is the first of what will be one detail DTO per subcategory.

## Rules

`car_event_organizer_rules` holds an organizer's free-text rules for the event ("no burnouts", "park
in marked bays"), ordered by `sort_order`. Despite the table name, this is a plain event feature, not
car-meet specific — `rules` sits alongside `organizers` at the top level of `MapEventDto`, not nested
under `carMeet`.

`CreateMapEventRequest.rules` is optional and, when non-empty, is saved in the **same transaction**
as the event — one `POST /` covers title, description, timings and rules together; no follow-up call
needed (rules have no dependency that would force a second request, unlike the cover image below).

There is no per-rule endpoint: `PUT /{eventId}/rules` replaces the whole list in one call, in the
order given, deleting the old rows and re-inserting with `sort_order` set from list position (the
`(event_id, sort_order)` unique index means the delete must land before the insert). It's how rules
get added after creation, reordered, or cleared (empty list). Same lock as the rest of the event:
organizer only, and only while pending/rejected.

## Cover images

`car_events.cover_image_url` stores an R2 **object key**, not a URL, resolved on read via
`StorageService.publicUrl(StorageBucket.MAP_EVENTS, key)` — same convention as avatars and business
logos.

Flow (identical to posts and the garage "add car" wizard, which is why the column is nullable):

1. `POST /api/v1/map-events` — create the event; the response carries its new `id`.
2. `GET /api/storage/events/{eventId}/cover` — a presigned PUT slot, `{key, uploadUrl}`.
   Organizers only (`MapEventUploadAccessPolicy`).
3. Flutter `PUT`s the image straight to R2.
4. `PATCH /api/v1/map-events/{eventId}/cover` — send the `key`.

Keys are `events/{eventId}/{uuid}.webp` in the `MAP_EVENTS` bucket (`cloudflare.r2.map-events.*`).
The random suffix — rather than a fixed `cover.webp` — means replacing a cover writes a new key, so
the CDN can never serve a stale copy; the previous object is deleted after the transaction commits.
`saveCoverImageKey` **requires the key to start with `events/{eventId}/`**, so one event cannot
claim another's upload.

## Location search (geocoding)

The create-event location picker has dedicated address fields (street, number, city, region,
postcode, country) rather than a single free-text box, so the map can recentre on the address the
organizer actually means. That means calling Mapbox's forward-geocoding API, and — like every other
Mapbox REST call this app makes — it is proxied through `geocode`/`MapboxGeocodingClient` rather
than called directly from Flutter: the access token (`mapbox.access-token`, from
`MAPBOX_ACCESS_TOKEN`) never reaches the client, and request volume stays under this backend's
control instead of whatever the app does.

`MapboxGeocodingClient` is a plain `RestClient` wrapper (same shape as `admin`'s
`SupabaseAuthAdminClient`), hitting the **Geocoding v6** `/search/geocode/v6/forward` endpoint in
its **structured input** mode — separate query params per address component, rather than one search
string — which Mapbox documents as materially more accurate, especially for machine-entered data
like this picker's. `GeocodeQuery.isBlank()` (every field null/blank) short-circuits to `[]` without
a Mapbox request, same idea as blank `q` used to be for `searchOrganizerCandidates`.
`proximity_lat`/`proximity_lng` are still optional and only applied as a bias when *both* are
present and valid coordinates — an invalid or lone value is silently dropped rather than failing the
search, since it's only a relevance hint, not a validated field. The request also always sets
`autocomplete=false` (Mapbox's own recommendation for structured input) and `limit=5`.

**Always queried with `permanent=false`.** Mapbox's ToS distinguishes results a caller intends to
store long-term (`permanent=true`, needs billing on the Mapbox account) from ones used transiently.
Here, the candidate the organizer taps only recentres and zooms the map — it does not get written to
`car_events.location` as-is. The organizer then drops their own pin on the map, and *that* coordinate
(the user's own input, not Mapbox's result) is what gets saved, so `permanent=false` applies.

`geocode` deliberately carries no `@Transactional`: it touches no repository, only an outbound HTTP
call, and the datasource pool is sized to 2 connections (`application.yaml`) — holding one idle for
a Mapbox round-trip would be a bad trade. There is no caching or per-user rate limiting yet; the
proxy itself is the control point, and either can be added later without a contract change.

Response candidates carry `feature_type` (`address`, `street`, `place`, ...) and `accuracy`
(`rooftop`, `parcel`, ... — only meaningful for `address`-level hits) alongside `place_name`, so the
picker's result list can show what kind of match each candidate is, not just its formatted text.

## Public API — `MapEventsService`

| Method | REST | Notes |
|---|---|---|
| `findNearby(lat, lng, radiusKm, categoryId, limit)` | `GET /nearby?lat=&lng=&radius_km=25&category=&limit=200` | `MapEventPinDto` list, nearest first |
| `search(query, lat, lng, statuses, cursor, size)` | `GET /search?q=&lat=&lng=&status=upcoming,live&cursor=&size=20` | `MapEventPageDto<MapEventPinDto>` — the map's search box; see § Map search |
| `listCategories()` | `GET /categories` | subcategory reference data |
| `geocode(query, proximityLat, proximityLng)` | `GET /geocode?address_line1=&address_number=&street=&block=&place=&region=&postcode=&locality=&neighborhood=&country=&proximity_lat=&proximity_lng=` | `GeocodeCandidateDto` list, best match first, up to 5 — proxies Mapbox Geocoding v6 structured input |
| `getEvent(userId, eventId)` | `GET /{eventId}` | full page + the viewer's own standing |
| `listAttendees(...)` | `GET /{eventId}/attendees?status=&cursor=&size=` | keyset page |
| `listParticipants(...)` | `GET /{eventId}/cars?status=&cursor=&size=` | defaults to the accepted line-up |
| `listMyParticipants(...)` | `GET /{eventId}/cars/mine` | the caller's own rows, any status — the only way to see your own pending/rejected entry |
| `getMyEvents(...)` | `GET /mine?cursor=&size=` | includes pending / rejected |
| `createEvent(...)` | `POST /` → 201 | submitted as `pending` |
| `updateEvent(...)` | `PATCH /{eventId}` | pending/rejected only |
| `saveCoverImageKey(...)` | `PATCH /{eventId}/cover` | |
| `replaceRules(...)` | `PUT /{eventId}/rules` | whole-list replace, pending/rejected only |
| `cancelEvent(...)` / `markFinished(...)` | `POST /{eventId}/cancel` / `/finish` | organizer, idempotent |
| `deleteEvent(...)` | `DELETE /{eventId}` → 204 | **creator only** |
| `addOrganizer(...)` / `removeOrganizer(...)` | `POST /{eventId}/organizers`, `DELETE /{eventId}/organizers/{id}` | creator only |
| `searchOrganizerCandidates(query)` | `GET /organizers/search?q=` | merged user + business search, for the add-organizer picker |
| `setAttendance(...)` / `removeAttendance(...)` | `PUT` / `DELETE /{eventId}/attendance` | returns the full `MapEventDto`, freshly reloaded |
| `registerCar(...)` / `withdrawCar(...)` | `POST /{eventId}/cars` → 201, `DELETE /{eventId}/cars/{carId}` | returns the full `MapEventDto`, not the participant row — `attending_cars_count` and `viewer.my_registered_car_ids` both move as a result, so one response saves a follow-up `GET` |
| `decideParticipant(...)` | `PATCH /{eventId}/cars/{carId}` | organizer verdict; `reason` required when rejecting; returns the full `MapEventDto` |
| `requestWithdrawal(...)` | `POST /{eventId}/withdraw` | flags every accepted row of the caller's as `withdrawn`, not removed; returns the full `MapEventDto` |
| `listWithdrawalRequests(...)` | `GET /{eventId}/withdrawals` | organizer only, one entry per requesting owner |
| `approveWithdrawal(...)` | `POST /{eventId}/withdrawals/{ownerId}/approve` | organizer only; hard-deletes the rows; returns the full `MapEventDto` |
| `rejectWithdrawal(...)` | `POST /{eventId}/withdrawals/{ownerId}/reject` | organizer only; rows revert to `accepted`; returns the full `MapEventDto` |

### Contests — `MapEventContestsService`

Every write answers with the fresh `ContestDto`, so no write needs a follow-up read.

| Method | REST | Notes |
|---|---|---|
| `listCategories()` | `GET /contest-categories` | reference data; `custom` is the free-text one |
| `listContests(...)` | `GET /{eventId}/contests` | every contest with its board and the viewer's standing |
| `getContest(...)` | `GET /{eventId}/contests/{contestId}` | |
| `createContest(...)` | `POST /{eventId}/contests` → 201 | organizer only; max 20 per event |
| `updateContest(...)` | `PATCH /{eventId}/contests/{contestId}` | partial; once `open` only `closes_at` and `criteria` may move. Editing times never opens or closes anything |
| `openContest(...)` | `POST /{eventId}/contests/{contestId}/open` | organizer; starts voting now and tells the meet; idempotent |
| `finishContest(...)` | `POST /{eventId}/contests/{contestId}/finish` | organizer; closes voting now and pays the podium; idempotent |
| `deleteContest(...)` | `DELETE /{eventId}/contests/{contestId}` → 204 | `scheduled` only |
| `requestEntry(...)` | `POST /{eventId}/contests/{contestId}/entries` | owner of a car already accepted to the event; re-sending a live entry is a no-op |
| `withdrawEntry(...)` | `DELETE /{eventId}/contests/{contestId}/entries/{carId}` | a pending request any time; an accepted entry only while `scheduled` |
| `decideEntry(...)` | `PATCH /{eventId}/contests/{contestId}/entries/{carId}` | organizer verdict; `reason` required when rejecting; rejecting mid-vote clears that car's votes |
| `vote(...)` | `PUT /{eventId}/contests/{contestId}/vote` | cast or change; 403 when neither RSVP'd `attending` nor holding an accepted car in the line-up, or for your own car, 409 while `scheduled` or once `finished` |
| `listMyParticipantCards(...)` | `GET /{eventId}/cards` | the caller's participant cards, one per accepted car; empty until the event is marked finished (§ Participant cards) |
| `findParticipantCards(keys)` | — | batch derivation for the `posts` feed; not viewer-scoped |
| `findOwnedParticipantCard(...)` | — | the ownership check behind sharing a card to the feed |
| `getCarHistory(carId)` | `GET /cars/{carId}/history` | the car's attended events and podium places, newest first |

Base path `/api/v1/map-events`. All endpoints require authentication, except the one below.

### Public event page — `getPublicEvent(eventId)`

`GET /public/v1/events/{eventId}` (`PublicMapEventController`) is **unauthenticated** — it sits
under `/public/**` next to the public car route and is called by the Tweakd-Web-App Worker for
`web.tweakdapp.com/e/{eventId}`, the link the app's share button hands out. It returns
`PublicMapEventDto`, a hand-written projection: title, description, category label, venue and
coordinates, times, cover, a clock-derived `phase`, the two head-counts, organizer credits (no ids)
and rule texts. Never attendee names, ids, approval state or the rejection reason.

| Event | Response |
|---|---|
| approved, upcoming / live / finished | 200 (`phase` = `upcoming` / `live` / `previous`, same 24h rule as the map search) |
| approved, then cancelled | 410 (`MapEventGoneException`) |
| pending, rejected, `hidden`, deleted, unknown | 404 — indistinguishable, like `getEvent` |
| malformed id | 400 from Spring's UUID conversion, before any query |

Events are shared by UUID, not a code: an approved event is already visible to every app user, so
there is nothing to pause, revoke or count. Cached `public, max-age=60, s-maxage=300` like the car.
Organizers whose account no longer resolves are dropped from the credits.

### Admin surface

The `admin` module owns the dashboard endpoints and the capability check; everything below just
delegates here. **`APPROVE_EVENTS` is held by `owner` and `senior_admin` only** — content moderators
handle reported content, but publishing to the map is a separate decision.

| Method | REST (`/api/v1/admin/map-events`) |
|---|---|
| `listEventsForReview(status, cursor, size)` | `GET /?status=pending` — **oldest first**, so nothing waits indefinitely |
| `countPendingReview()` | `GET /counts` |
| `getEventAsAdmin(eventId)` | `GET /{eventId}` — bypasses the unapproved-event visibility rule |
| `approveEvent(eventId)` | `POST /{eventId}/approve` |
| `rejectEvent(eventId, reason)` | `POST /{eventId}/reject` — reason required |
| `deleteEventAsAdmin(eventId)` | `DELETE /{eventId}` → 204 |

Contests have their own small admin surface, on `MapEventContestsService` and gated on
**`MANAGE_CONTESTS`** (owner / senior admin — force-finishing pays out reputation and badges that
cannot be cleanly taken back):

| Method | REST (`/api/v1/admin/contests`) |
|---|---|
| `listContestsForReview(status, limit)` | `GET /?status=open&limit=50` — longest-running first; `scheduled` and `finished` are accepted for auditing |
| `countOpenContests()` | `GET /counts` |
| `finishContestAsAdmin(contestId, staffId)` | `POST /{contestId}/finish` — 409 if it never opened |

It exists because nothing closes a contest on a timer any more (`CONTESTS_MANUAL_LIFECYCLE.md`): an
abandoned contest takes votes forever and no organizer means nobody in the app can end it. The
force-finish is the organizer's own path — `ContestFinalizer.finalizeLocked` under the same row
lock, awards suppressed only when the event was cancelled — with the staff id written to
`finished_by`, which has no FK precisely so a non-profile id can go there.

## Map search

`search` backs the map screen's search box: approved events whose title, category label or venue
name contains `q`, **anywhere** (no radius), nearest to `lat`/`lng` first, keyset-paged on
`(st_distance, id)` exactly like the business search (same escaping, 2-character minimum, 1..50 page
size, cursor valid only against the same centre and status filter).

**The phase is derived from the clock, not read from `status`** — nothing sweeps that column, and
in practice no event is ever stored as `live`. The query computes it with the map's own rule:

| Phase | Rule |
|---|---|
| `previous` | stored `previous`, or `coalesce(ends_at, starts_at + 24h) < now()` |
| `live` | otherwise, stored `live` or `starts_at <= now()` |
| `upcoming` | everything else |

`status` filters on that phase (any of `upcoming`, `live`, `previous`; default `upcoming,live`;
anything else is a 400), and each returned pin's `status` **is** the derived phase. Canceled,
hidden and unapproved events never match. Unlike `/nearby`, past events are reachable — the app
shows them behind a "Past" chip.

**Known mismatch:** `MapEventEntity.hasFinished` has no 24-hour rule for an event without
`ends_at`, so such an event can read as `previous` here while `viewer.can_rsvp` still says true.
The map query already had this gap; search just makes those events reachable.

## Visibility

An unapproved event is visible only to its organizers (and to admins via `getEventAsAdmin`).
Everyone else gets `MapEventNotFoundException` — the *same* 404 as a nonexistent id, so pending and
rejected submissions cannot be discovered by probing ids. `rejection_reason` is stripped from the
DTO for non-organizers. Pending/rejected car entries are organizer-only for the same reason: they
would otherwise reveal whose car was turned away. The owner of a pending/rejected car still sees
their own row (status, and the rejection reason if any) through `listMyParticipants`.

## Notifications

Published as Spring domain events from this module's public API and consumed asynchronously,
after commit, by `notification`'s `MapEventsNotificationListener` — so this module has no dependency
back on `notification` (no Modulith cycle), and a rolled-back approval never notifies.

| `type` | Fired when | Recipient | Pref gate |
|---|---|---|---|
| `map_event_approved` | an admin approves your event | creator | none |
| `map_event_rejected` | an admin rejects it (reason in `body`) | creator | none |
| `map_event_car_decided` | an organizer accepts/rejects your car (reason in `body` when rejected) | car owner | none |
| `map_event_car_registered` | a car is entered and needs a decision | individual organizers, minus the actor | `event_organizer_enabled` |
| `map_event_organizer_added` | you are credited as a co-organizer | the added user | `event_organizer_enabled` |
| `contest_entry_requested` | a car asks to enter one of your contests | individual organizers, minus the actor | `event_organizer_enabled` |
| `contest_entry_decided` | an organizer accepts/rejects your contest entry (reason in `body` when rejected) | car owner | none |
| `contest_opened` | voting opened on a contest at an event you are attending | attendees | `organized_events_enabled` |
| `contest_results` | a contest you could vote in has finished | attendees | `organized_events_enabled` |
| `contest_placed` | your car finished on the podium | each podium owner | none |
| `participant_card_ready` | an organizer marked the event finished, so your card exists | each owner of an accepted car, once per event (not per car; not on a repeat finish) | none |

The first three are decisions about the recipient's **own** submission, so they are ungated, like
`feedback_status` and `moderation_warning` — you do not opt out of being told what happened to
something you submitted. The last two are the running-an-event notifications aimed at organizers,
gated on `event_organizer_enabled`. `contest_entry_requested` follows the organizer rule and
`contest_entry_decided` / `contest_placed` follow the own-submission rule, for the same reasons.
`contest_opened` and `contest_results` are the first producers of `organized_events_enabled` — they
are attendee-facing event logistics ("go vote", "results are in"), not decisions about anything the
recipient submitted.

## Contests

A contest is a category vote inside one event — "best exhaust", "loudest build" — created by an
organizer, entered by the cars already accepted to the event, and voted on by the people at the event — `attending` RSVPs and owners of accepted cars —
it. An event can hold up to **20** contests and a contest up to **40** entries.

### State machine

`scheduled` → `open` → `finished`, plus `canceled` when the meet itself is cancelled.

**Both transitions are an organizer's tap. Nothing here runs on a timer** — the same call the owner
made for event status. A contest is born `scheduled`, goes `open` on `POST /open`, and `finished` on
`POST /finish` or when the event itself is marked finished or cancelled (that cascade closes every
still-open contest in the same transaction).

- `opens_at` / `closes_at` are the **planned window, shown in the app and never enforced**. Voting
  does not start when `opens_at` passes and does not stop when `closes_at` does; `finished_early`
  just records whether the finish landed before the planned end. They are still bounded at write
  time so the label reads sensibly: `closes_at > opens_at`, and no more than 12h after the event
  ends.
- While `scheduled`, the organizer may edit anything or delete the contest outright; once `open`,
  only `closes_at` and `criteria` may change.
- `POST /open` requires an **approved** event that is not over, so a submission that never gets
  published cannot start paying out reputation.

### Voting

One row per (contest, voter), upserted — voting again *changes* the vote rather than adding one.
Eligibility, in order: signed in, attendance = `attending` on that event, contest `open`, and the
target car is a live entry that is not the voter's own. `votes_count` on both the contest and the
entry is owned by the `trg_contest_vote_counts()` trigger, so a vote is one upsert and no recount;
`entries_count` likewise comes from `trg_contest_entries_count()`. Nothing in the read path
aggregates votes.

### Finalising

`ContestFinalizer` takes a **pessimistic row lock** on the contest before it does anything, so two
organizers tapping `POST /finish` — or one of them racing the event being marked finished — cannot
both pay the podium. Ranking is votes desc →
earliest `last_vote_at` first (a tie goes to whoever reached the number first) → car id, and only
entries with **at least one vote** may take a podium place — an unvoted contest finishes with no
winners rather than an arbitrary top 3. Awards go out through the existing SPIs, so neither
`reputation` nor `badges` knows what a contest is:

- `ReputationService.award` with `CONTEST_FIRST_PLACE` / `CONTEST_SECOND_PLACE` /
  `CONTEST_THIRD_PLACE` and `ReputationSourceType.CONTEST` — idempotent by partial unique index, so
  a replayed finalisation pays nothing twice.
- `BadgeService.awardForTrigger` with one trigger per rank — `contest_first_place`,
  `contest_second_place`, `contest_third_place` — which is what keeps the gold, silver and bronze
  medals separate.

### Realtime

Live standings ride Supabase Realtime **Broadcast** on the private topic `event:<eventId>:contests`,
published by the backend with the service key. `ContestBoardPublisher` marks a contest dirty on
every vote and flushes on a 1.5s debounce, so a burst of votes is one message, not one per vote.
Clients are read-only: the `realtime.messages` SELECT policy lets any signed-in user read a topic
belonging to an approved event and lets nobody publish. The app also polls every 30s, so a channel
that will not join degrades to a slower board rather than a stuck one.

### Car history

`GET /map-events/cars/{carId}/history` returns the events a car attended with the podium places it
took, newest first, capped at 50. It is the permanent record behind the "attended events" section
of a car's page and survives the event finishing.

### Cascades

Contest state is kept consistent with event state from `MapEventsServiceImpl`: cancelling or
finishing an event finalises every open contest (a cancelled meet pays **nothing**), rejecting a
previously accepted car deletes its contest entries, and an approved withdrawal deletes the
owner's entries before the participant rows go.

## Cross-module dependencies

`profile` (organizer/attendee identities via `findByIds`, notification preferences), `garage`
(`findCarsByIds`, `findCarOwnerIds` — car ownership lives on the car's *garage*, not the car),
`business` (`findBusinessRefsByIds`, added for this module), `storage` (cover keys → public URLs),
and `shared.geo.GeoSupport` for lat/lng ↔ JTS `Point` (PostGIS order is lng, lat).
Contests add `reputation` and `badges` (podium awards, through their existing SPIs) and
`shared.realtime.SupabaseBroadcastClient` (live boards).

## Radius search

`findNearby` is a native PostGIS query. `ST_DWithin` on the `geography` column is index-aware, so
Postgres uses the GiST index `car_events_location_idx`; a bare `ST_Distance(...) < x` would force a
sequential scan. Search parameters are bounded (`radius_km` ≤ 500, `limit` ≤ 500, WGS84 range, NaN
and infinity rejected) so a hand-crafted request cannot turn the spatial index into a table scan.
The **centre point is supplied by the client** — realtime location if permission was granted, else
the user's home city — exactly as for `business`.

## Schema notes

The tables were created in Supabase by hand and hardened by the `car_events_schema_hardening`
migration (all tables were empty at the time):

- `created_by` added (there was no record of who created an event); `car_event_organizers.role`
  added with a CHECK, a "creator must be an individual" CHECK, and a one-creator-per-event index.
- `location` tightened from untyped `geography` to `geography(Point,4326)`, made NOT NULL, and given
  a GiST index — a map pin without coordinates is meaningless, and without the index every radius
  query is a scan.
- `cover_image_url` made **nullable** for the create-then-attach flow.
- `rejection_reason` and `updated_at` added.
- `canceled` seeded into `car_event_status_options`.
- Indexes added: `(approval_status, status, starts_at)`, `starts_at`, `car_event_participants(owner_id)`.
- `car_event_participants.rejection_reason` (nullable text) added by
  `add_rejection_reason_to_car_event_participants`, mirroring `car_events.rejection_reason`.

The four contest tables were added by the `car_event_contests` migration
(`20260908120000_car_event_contests.sql`):

- `car_event_contest_categories` — six seeded rows plus `custom`; label and icon come from here so
  the app never hardcodes the list.
- `car_event_contests` — one per category per event, with `opens_at` / `closes_at`, `status`, and
  trigger-owned `votes_count` / `entries_count`.
- `car_event_contest_entries` — `(contest_id, car_id)`, `pending`/`accepted`/`rejected`/`withdrawn`,
  plus the frozen `final_rank` once the contest is finished.
- `car_event_contest_votes` — `(contest_id, voter_id)` primary key, which is what makes a vote a
  change rather than an addition.
- `car_event_participants_car_id_idx` added for the car-history read.
- The `badges` CHECK is `('account_created','contest_first_place','contest_second_place',
  'contest_third_place')`, with the three medal rows seeded.

`car_events` has **RLS enabled with no policies**, and the other `car_event*` tables have no table
grants, so the whole feature is backend-gated — the same posture as `business`.

## Not built yet

- **Automatic status transitions** — deferred; see above.
- **Subcategories beyond `car_meet`** — the shape is in place (`car_event_categories` +
  a detail table + a detail DTO per category).
- **Business organizer permissions** — blocked on business login existing at all.

## Participant cards

Every **participant** — a car with an `accepted` row in `car_event_participants` — gets a
shareable card once an organizer marks the event finished. Spectators (RSVPs) get none. The
card lists every finished contest that car entered, naming a place only inside the podium with at
least one vote (the same rule `ContestFinalizer` pays out on), and carries the car's best such
place for the app's placement pill.

**Cards are derived, not stored.** A card *is* the pair `(event_id, car_id)` (`ParticipantCardKey`),
computed on read by `deriveCards` from data that is frozen once contests finish — so nobody, the
owner included, can create or edit one, and there is nothing to generate, backfill or keep in sync.
`deriveCards` resolves any number of keys in a fixed number of queries (events, participant rows,
finished contests, their entries, one car batch).

A pair is a card only when **all** hold:

- the event is admin-approved and its `status` is `previous` — an organizer marked it finished.
  The clock alone does not count: an event past its `ends_at` but never marked finished has no
  cards (owner's rule, 2026-09-11). A cancelled event has none;
- the car's participant row is `accepted`;
- the car still resolves through `garage` (otherwise the key is dropped rather than drawn broken).

`markFinished` publishes `ParticipantCardsReadyEvent` (one recipient per owner, de-duplicated) on
the **first** finish only; a repeat tap does not re-notify. The `notification` module turns it into
the ungated `participant_card_ready` push, whose payload is `event_id` — the event page lists the
recipient's cards.

Sharing a card to the feed belongs to `posts` (`POST /api/v1/posts/participant-card`, with a repost
cooldown); it only calls `findOwnedParticipantCard` here. Every contest read also embeds a
`ContestEventSummaryDto` (title, cover, place, start, head-counts, contest count and the event
`status`), so a contest opened from a deep link carries its event context and the app can tell
whether cards exist yet.
