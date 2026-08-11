# mapevents module

Owns **car events on the map** — the pins users tap to find meets near them, the floating widget
behind a pin, and the full event page (organizers, the car line-up, attendees; contests later).

Users create events from the app; **an admin must approve one before it appears on the map**.

## Naming

The module is "map events" (it is a map feature) and lives at `/api/v1/map-events`; the Supabase
tables it owns are named `car_event*`. Types here carry the `MapEvent` prefix and name their table
explicitly. Tables owned: `car_events`, `car_event_categories`, `car_event_organizers`,
`car_event_attendees_list`, `car_event_participants`, `event_car_meet`, plus the three status
reference tables.

## Two independent states

| Column | What it means | Who moves it |
|---|---|---|
| `approval_status` | the admin gate: `pending` → `accepted` / `rejected` | admins only |
| `status` | the event's own life: `upcoming` / `live` / `previous` / `hidden` / `canceled` | organizers |

**Nothing sweeps `status` automatically.** Automatic `upcoming → live → previous` transitions were
deliberately deferred by the owner, so there is no `@Scheduled` job here. Two consequences the code
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
  otherwise it is `accepted` outright.

Both counts (`attendees_count`, `attending_cars_count`) are **trigger-owned**
(`trg_update_car_event_attendees_count`, `trg_update_car_event_attending_cars_count`); this module
never writes a count column. After an RSVP change the row is re-read (`entityManager.refresh`) so
the response shows the new total rather than the stale one in the session.

## Subcategories

`car_events.event_type` → `car_event_categories`. Only `car_meet` exists today, and each category
gets its own detail table — the car meet's is `event_car_meet` (`registration_deadline`).
`is_available` gates a category on the create screen without invalidating existing events.
`CarMeetDetailsDto` is the first of what will be one detail DTO per subcategory.

## Cover images

`car_events.cover_image_url` stores an R2 **object key**, not a URL, resolved on read via
`StorageService.publicUrl(StorageBucket.MAP_EVENTS, key)` — same convention as avatars and business
logos.

Flow (identical to posts and the garage "add car" wizard, which is why the column is nullable):

1. `POST /api/v1/map-events` — create the event; the response carries its new `id`.
2. `GET /api/storage/events/{eventId}/cover` — a presigned PUT slot, `{key, uploadUrl}`.
3. Flutter `PUT`s the image straight to R2.
4. `PATCH /api/v1/map-events/{eventId}/cover` — send the `key`.

Keys are `events/{eventId}/{uuid}.webp` in the `MAP_EVENTS` bucket (`cloudflare.r2.map-events.*`).
The random suffix — rather than a fixed `cover.webp` — means replacing a cover writes a new key, so
the CDN can never serve a stale copy; the previous object is deleted after the transaction commits.
`saveCoverImageKey` **requires the key to start with `events/{eventId}/`**, so one event cannot
claim another's upload.

## Public API — `MapEventsService`

| Method | REST | Notes |
|---|---|---|
| `findNearby(lat, lng, radiusKm, categoryId, limit)` | `GET /nearby?lat=&lng=&radius_km=25&category=&limit=200` | `MapEventPinDto` list, nearest first |
| `listCategories()` | `GET /categories` | subcategory reference data |
| `getEvent(userId, eventId)` | `GET /{eventId}` | full page + the viewer's own standing |
| `listAttendees(...)` | `GET /{eventId}/attendees?status=&cursor=&size=` | keyset page |
| `listParticipants(...)` | `GET /{eventId}/cars?status=&cursor=&size=` | defaults to the accepted line-up |
| `getMyEvents(...)` | `GET /mine?cursor=&size=` | includes pending / rejected |
| `createEvent(...)` | `POST /` → 201 | submitted as `pending` |
| `updateEvent(...)` | `PATCH /{eventId}` | pending/rejected only |
| `saveCoverImageKey(...)` | `PATCH /{eventId}/cover` | |
| `cancelEvent(...)` / `markFinished(...)` | `POST /{eventId}/cancel` / `/finish` | organizer, idempotent |
| `deleteEvent(...)` | `DELETE /{eventId}` → 204 | **creator only** |
| `addOrganizer(...)` / `removeOrganizer(...)` | `POST /{eventId}/organizers`, `DELETE /{eventId}/organizers/{id}` | creator only |
| `setAttendance(...)` / `removeAttendance(...)` | `PUT` / `DELETE /{eventId}/attendance` | |
| `registerCar(...)` / `withdrawCar(...)` | `POST /{eventId}/cars` → 201, `DELETE /{eventId}/cars/{carId}` | |
| `decideParticipant(...)` | `PATCH /{eventId}/cars/{carId}` | organizer verdict |

Base path `/api/v1/map-events`. All endpoints require authentication; none are under `/public/**`.

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

## Visibility

An unapproved event is visible only to its organizers (and to admins via `getEventAsAdmin`).
Everyone else gets `MapEventNotFoundException` — the *same* 404 as a nonexistent id, so pending and
rejected submissions cannot be discovered by probing ids. `rejection_reason` is stripped from the
DTO for non-organizers. Pending/rejected car entries are organizer-only for the same reason: they
would otherwise reveal whose car was turned away.

## Notifications

Published as Spring domain events from this module's public API and consumed asynchronously,
after commit, by `notification`'s `MapEventsNotificationListener` — so this module has no dependency
back on `notification` (no Modulith cycle), and a rolled-back approval never notifies.

| `type` | Fired when | Recipient | Pref gate |
|---|---|---|---|
| `map_event_approved` | an admin approves your event | creator | none |
| `map_event_rejected` | an admin rejects it (reason in `body`) | creator | none |
| `map_event_car_decided` | an organizer accepts/rejects your car | car owner | none |
| `map_event_car_registered` | a car is entered and needs a decision | individual organizers, minus the actor | `event_organizer_enabled` |
| `map_event_organizer_added` | you are credited as a co-organizer | the added user | `event_organizer_enabled` |

The first three are decisions about the recipient's **own** submission, so they are ungated, like
`feedback_status` and `moderation_warning` — you do not opt out of being told what happened to
something you submitted. The last two are the running-an-event notifications aimed at organizers,
gated on `event_organizer_enabled`. `organized_events_enabled` is reserved for attendee-facing
event logistics (delays, cancellations) and has no producer yet.

## Cross-module dependencies

`profile` (organizer/attendee identities via `findByIds`, notification preferences), `garage`
(`findCarsByIds`, `findCarOwnerIds` — car ownership lives on the car's *garage*, not the car),
`business` (`findBusinessRefsByIds`, added for this module), `storage` (cover keys → public URLs),
and `shared.geo.GeoSupport` for lat/lng ↔ JTS `Point` (PostGIS order is lng, lat).

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

`car_events` has **RLS enabled with no policies**, and the other `car_event*` tables have no table
grants, so the whole feature is backend-gated — the same posture as `business`.

## Not built yet

- **Contests** — a planned event sub-feature; nothing exists for it yet.
- **Automatic status transitions** — deferred; see above.
- **Subcategories beyond `car_meet`** — the shape is in place (`car_event_categories` +
  a detail table + a detail DTO per category).
- **Business organizer permissions** — blocked on business login existing at all.
