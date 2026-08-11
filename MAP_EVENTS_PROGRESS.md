# Map Events — progress tracker

Feature branch: `map-events`. Started 2026-08-11.

Car events shown as pins on the app map. Users create them; app admins approve before they go live.
Tapping a pin opens a floating widget (summary); a dedicated page shows the full event
(participating cars, attendees, contests later). Events have subcategories — only **car meet**
exists today, more coming.

---

## Status

| Phase | State |
|---|---|
| 0. Schema audit | ✅ done (below) |
| 1. Open questions answered by owner | ✅ done — see Decisions |
| 2. Supabase hardening migration | ✅ applied + verified |
| A. Storage wiring (`MAP_EVENTS` bucket) | ✅ done |
| B. Module skeleton + entities | ✅ done |
| C. Repositories | ✅ done |
| D. DTOs + exceptions | ✅ done |
| E. Service interface | ✅ done |
| F. Service impl (read + write + RSVP + cars) | ✅ done |
| G. Controller | ✅ done |
| H. Admin approval (capability + endpoints) | ✅ done |
| I. Notifications | ✅ done |
| J. README + CONTEXT.md | ✅ done |
| K. Compile + ModularityTests | ✅ passing |
| L. Tests | ✅ 54 new, suite green at 516 |

**The feature is complete and the whole suite passes.** What remains is listed under
"Still open / deliberately deferred" and "Follow-ups for the owner" below.

**Module naming (owner-chosen):** package `com.carsocialmedia.backend.mapevents`, REST base
`/api/v1/map-events`, types prefixed `MapEvent*`, tables stay `car_event*`. R2 keys:
`events/{eventId}/{uuid}.webp` in the `MAP_EVENTS` bucket (`cloudflare.r2.map-events.*`, which was
already present in `application.yaml` — only the `StorageBucket` enum and `R2Config.target()` needed
the new case).

---

## Schema audit (live Supabase, project `fybgmaigzidhbmhbgfhu` / "Tweakd")

All ten tables exist already, all with RLS enabled. Created by hand — not yet hardened.

### `car_events`
| Column | Type | Null | Default |
|---|---|---|---|
| `id` | uuid PK | NO | `gen_random_uuid()` |
| `created_at` | timestamptz | NO | `now()` |
| `event_type` | text → `car_event_categories(id)` | NO | — |
| `title` | text | NO | — |
| `description` | text | NO | — |
| `location_name` | text | NO | — |
| `starts_at` | timestamptz | NO | — |
| `ends_at` | timestamptz | YES | — |
| `cover_image_url` | text | NO | — |
| `location` | `geography` (untyped!) | YES | — |
| `status` | text → `car_event_status_options(id)` | NO | — (no default) |
| `attendees_count` | int | NO | 0 (trigger-maintained) |
| `attending_cars_count` | int | NO | 0 (trigger-maintained) |
| `requires_participant_approval` | bool | NO | `true` |
| `approval_status` | text → `car_event_approval_status_options(id)` | NO | `'pending'` |

Only index: the PK. No GiST index on `location`.

### Reference tables (seeded)
- `car_event_categories` — `car_meet` / "Car meet", `is_available = true`
- `car_event_status_options` — `upcoming`, `live`, `previous`, `hidden`
- `car_event_approval_status_options` — `pending`, `accepted`, `rejected`
- `car_event_attendee_status` — `attending`, `interested`
- `car_event_participant_status_options` — `pending`, `accepted`, `rejected`

### Join tables
- **`car_event_organizers`** — `id` uuid PK, `event_id`, `individual_organizer_id` → `profiles`,
  `business_organizer_id` → `business_accounts`. CHECK `num_nonnulls(individual, business) = 1`.
  Partial unique indexes per (event, organizer) on each side. **No creator/role marker.**
- **`car_event_attendees_list`** — PK `(user_id, event_id)`, `status` → attendee status.
  Trigger `trg_update_car_event_attendees_count` maintains `car_events.attendees_count`.
- **`car_event_participants`** — PK `(event_id, car_id)`, `owner_id` → `profiles`, `car_id` → `cars`,
  `status` default `pending`. Trigger `trg_update_car_event_attending_cars_count` maintains
  `car_events.attending_cars_count`.
- **`event_car_meet`** — PK `event_id`, `registration_deadline` timestamptz NOT NULL.
  The subcategory-detail table; one per subcategory going forward.

### Already in place elsewhere
- `application.yaml` already has `cloudflare.r2.map-events.{bucket,public-url}` →
  `CLOUDFLARE_MAP_EVENTS_BUCKET_NAME` / `_URL`. **Not yet wired** into `StorageBucket` (enum) or
  `R2Config.target(...)` — both need a new `MAP_EVENTS` case.

---

## Gaps found in the schema (need a hardening migration)

1. **No creator column.** `car_events` has no `created_by`; `car_event_organizers` has no role flag.
   Nothing records who originally created an event vs who was added later.
2. **`location` is nullable and untyped `geography`.** A map pin must have coordinates.
   Should become `geography(Point,4326) NOT NULL` + GiST index (mirrors
   `business_accounts_location_idx`), otherwise every radius query is a seq scan.
3. **No approval audit fields** — no `reviewed_by`, `reviewed_at`, `rejection_reason`.
4. **No `updated_at`** on `car_events`.
5. **No index** on `approval_status` / `status` / `starts_at` — the map and "upcoming events" queries
   both filter on these.
6. **`status` has no default** even though every new event starts the same way.
7. `car_event_participants` has no index on `owner_id` (for "my car registrations").

---

## Decisions (owner-confirmed 2026-08-11)

1. **Creator model — both.** `car_events.created_by` (immutable, NOT NULL) *and* a `role` column on
   `car_event_organizers` (`creator` | `organizer`). The creator also gets an organizer row, so the
   organizer list is self-describing; ownership is unambiguous and unrevokable.
2. **Status is stored, not derived, and nothing sweeps it.** The owner set the `'upcoming'` default
   manually and **explicitly deferred automatic status switching — do not add a cron/@Scheduled job.**
   Organizers can mark an event finished (→ `previous`) and cancel it (→ `canceled`, newly seeded).
   Because nothing flips `upcoming` → `previous` on its own, the **map query also filters on time**
   (`ends_at`, falling back to `starts_at`) so stale events don't sit on the map forever.
3. **Cover image — create-then-attach**, the pattern posts and cars already use: create the event →
   presign against the real event id → `PATCH` the key back. `cover_image_url` is now nullable to
   allow the window between the two calls.
4. **Map query — radius**, identical in shape to `/businesses/nearby`.
5. **Approval — `owner` + `senior_admin` only.** New `APPROVE_EVENTS` capability (content moderators
   deliberately excluded). `rejection_reason` column added; no `reviewed_by` / `reviewed_at`.
6. **Approved events are locked to organizers.** Editing is allowed only while `pending` or
   `rejected`; editing a rejected event clears the reason and resubmits it as `pending`.
7. **Business co-organizers are allowed now**, display-only (no business login exists yet — the owner
   will wire that later; explicitly out of scope here).
8. **Cancel vs delete.** Organizer cancels → `status = 'canceled'` (page stays, pin gone).
   Hard delete removes the Supabase row outright (children cascade, R2 cover deleted):
   **creator can delete their own event, admins can delete any**; co-organizers cannot.
9. **Attendees are spectators, not participants.** Open RSVP (`attending` / `interested`) to any
   authenticated user, no organizer approval. RSVP stays open **while the event is live** and closes
   only when it finishes — `ends_at` has passed, or an organizer marked it `previous`. Canceled
   events are closed.
10. **Car registration** — only the car's owner may enter it; rejected after
    `event_car_meet.registration_deadline`. Starts `pending` when
    `requires_participant_approval`, else `accepted`. Organizers accept/reject.

---

## Migration applied

`car_events_schema_hardening` (applied 2026-08-11, all tables empty at the time):

- `car_events.created_by uuid NOT NULL → profiles(id)` + index
- `car_event_organizers.role text NOT NULL DEFAULT 'organizer'` + CHECK in (`creator`,`organizer`)
  + CHECK "creator must be an individual" + partial unique index (one creator per event)
- `car_events.location` → `geography(Point,4326) NOT NULL` + GiST index `car_events_location_idx`
- `car_events.cover_image_url` → nullable (create-then-attach flow)
- `car_events.rejection_reason text` added
- `car_events.updated_at timestamptz NOT NULL DEFAULT now()` added
- seeded `car_event_status_options` row `canceled` / "Canceled"
- indexes: `car_events_map_idx (approval_status, status, starts_at)`, `car_events_starts_at_idx`,
  `car_event_participants_owner_id_idx`

> Note `car_events` has RLS **enabled with no policies**, and the other `car_event_*` tables have no
> table grants — so the whole feature is backend-gated. Same posture as `business`.

## What was built

| Area | Files |
|---|---|
| Module | `mapevents/` — `MapEventsService`, 5 domain events, 8 DTOs, 7 request DTOs, 8 exceptions, 6 entities, 6 repositories, service impl, controller, README |
| Storage | `StorageBucket.MAP_EVENTS`, `R2Config.mapEvents`, `StorageService.eventCoverUploadUrlRequest`, `GET /api/storage/events/{eventId}/cover` |
| Admin | `Capability.APPROVE_EVENTS` (owner + senior_admin only), `AdminMapEventsController`, `EventRejectionRequest` |
| Notifications | `MapEventsNotificationListener` + 5 notification types; first producer for the previously unused `organized_events_enabled` preference |
| Business | `BusinessRefDto` + `BusinessService.findBusinessRefsByIds` (batch name/logo lookup, added so crediting business organizers isn't an N+1) |
| Tests | `MapEventRepositoryIT` (16), `MapEventsServiceImplTest` (24), `MapEventControllerWebTest` (11), `AdminRoleCapabilityTest` (3) |

### Bug caught by the tests (would have shipped)

The keyset queries originally used a bare `:cursorCreatedAt is null`. Postgres cannot infer the type
of a parameter that only ever appears as `? is null` and fails the whole statement with *"could not
determine data type of parameter $2"* — and the **first page is precisely the call that passes a
null cursor**, so every first request to "my events", the admin queue, the attendee list and the car
line-up would have 500'd. Fixed with `cast(:param as timestamp)` / `cast(:param as string)`, and the
reason is documented on `MapEventRepository` so it does not get "tidied up" again.

## Test infrastructure that had to be updated

- **`src/test/resources/db/schema.sql` was stale** (Aug 5, 69 tables) and predated every
  `car_event*` table. With `ddl-auto: validate` in tests, the new entities would have failed to boot
  *every* integration test. Regenerated → 96 tables.
  **`scripts/dump-schema.sh` had been deleted in commit `f8798ef`**, so it was recovered from
  `f8798ef^` and run from a temp copy. The generated `schema.sql` header still tells you to
  regenerate with `./scripts/dump-schema.sh` — **that script is currently missing from the repo.**
- **`db/seed.sql`** — added the five map-event reference tables' rows (categories, status, approval
  status, attendee status, participant status). The dump is schema-only, so RESTRICT FKs need them.
- **`src/test/resources/application.yaml`** — added `cloudflare.r2.map-events.*` and, while there,
  `cloudflare.r2.business.*`, which was already missing (`R2Config.target(BUSINESS)` would have
  returned null and NPE'd in any test that resolved a business logo).

## Still open / deliberately deferred

- Automatic `upcoming` → `live` → `previous` transitions (owner will decide later).
- Contests (a future event sub-feature).
- Business-organizer *permissions* — they are credit-only until business login exists.
- Subcategories beyond `car_meet` (`car_event_categories.is_available` already gates them).

## Follow-ups for the owner

1. ~~**Restore `scripts/dump-schema.sh`**~~ — done 2026-08-11: recovered from `f8798ef^`,
   `schema.sql` regenerated from live Supabase (96 tables, now byte-accurate — the manually
   patched version had been missing several trigger functions).
2. ~~**Notification gating**~~ — resolved 2026-08-11: `organized_events_enabled` was ambiguously
   named. Split it — new column `event_organizer_enabled` now gates the organizer-facing
   notifications ("a car needs your approval", "you were added as an organizer");
   `organized_events_enabled` is reserved for attendee-facing event logistics (delays,
   cancellations), which have no producer yet. Migration `add_event_organizer_enabled_notification_preference`
   applied to Supabase. Participant-facing notifications (contest votes, placements, car
   questions) deliberately deferred — no column added, since contests don't exist yet and the
   right shape isn't known.
3. **`endsAt` cannot be cleared** by `PATCH` — a record cannot tell "absent" from "explicit null",
   so an end time can be changed but not removed. Say the word if that needs an explicit
   `clear_ends_at` flag.
4. **Status transitions** are still fully manual by your decision. The map read path compensates
   (time filter, 24h grace for open-ended events), but "upcoming" will stay "upcoming" in the DB
   until an organizer acts.