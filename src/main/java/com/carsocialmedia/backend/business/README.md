# business module

Owns **business accounts** — real companies (repair shops, car washes, tuning shops, dealerships, …)
that appear as points on the app's virtual map and have their own in-app profile page.

## Business accounts are not user accounts

A business account is a wholly separate identity type. It is **not** owned by an individual
`profiles` row and carries no `owner_id`; the two can exist independently. Businesses are created
and managed from a separate business dashboard, mirroring how staff accounts are decoupled from app
accounts.

There is, as yet, **no link between `business_accounts` and `auth.users`** — business login has not
been designed. Until it is, this module is **read-only**: it serves the map and the business profile
screen. Adding login later means adding that link (either `business_accounts.id = auth.users.id` or
a separate `auth_user_id` column) and only then write endpoints and owner-scoped RLS.

## Visibility

Only businesses that are both `active_status = 'active'` **and** `verification_status = 'verified'`
are ever exposed. Pending submissions, rejected applications, and suspended or deleted businesses
are invisible to the app. This is enforced twice:

- in every query in this module (`findVisibleById`, `findVisibleNearby`), and
- in Supabase RLS, for anything holding an anon/authenticated key.

`getBusiness` throws the same `BusinessNotFoundException` for "no such id" and "hidden", so a
suspended business cannot be detected by probing ids.

## Opening hours and `is_open_now`

`is_open_now` is **derived on every request** and never stored — a stored flag is wrong the moment
the clock passes a closing time. It is computed by `internal/OpeningHours` from `business_hours`,
in the business's own `timezone`:

- **Per-business timezone.** `business_accounts.timezone` is an IANA zone id
  (`Europe/Bucharest` by default). Hours are wall-clock in that zone. A CHECK constraint
  (`business_accounts_timezone_valid`) rejects unrecognised ids at write time; `OpeningHours` still
  falls back to UTC and logs rather than failing a map load if one somehow slips through.
- **Shifts past midnight.** `closing_hour <= opening_hour` means the shift runs into the next day
  (`22:00 → 06:00`). Such a shift is checked against *yesterday's* row too, so at 02:00 on Tuesday
  the business is open because of Monday's entry. Equal times mean open around the clock.
- Hours for a whole page of map pins are fetched in **one** batched query, not per pin.

## Radius search

`findNearby` is a native PostGIS query. `ST_DWithin` on the `geography` column is index-aware, so
Postgres uses the GiST index `business_accounts_location_idx`; a bare `ST_Distance(...) < x`
comparison would force a sequential scan over every business. Results are ordered by `id`, not
distance — the map renders every pin at its own coordinates regardless of array order, so `id` is
just there to make `limit` deterministic if a radius ever has more matches than the limit allows.

The **centre point is supplied by the client**: the user's realtime location when they granted
location permission, otherwise the coordinates of their home city (already available client-side
from `ProfileService.listCities`). The backend does not currently resolve it — if that is wanted,
the profile module needs to expose the caller's realtime location or city coordinates.

Search parameters are bounded (`radius_km` ≤ 500, `limit` ≤ 500, coordinates in WGS84 range, NaN and
infinity rejected) so a hand-crafted request cannot turn the spatial index into a table scan.

## Public API — `BusinessService`

| Method | REST | Notes |
|--------|------|-------|
| `findNearby(lat, lng, radiusKm, typeId, limit)` | `GET /api/v1/businesses/nearby?lat=&lng=&radius_km=25&type=&limit=200` | `BusinessMapPinDto` list, nearest first. `type` filters by business type id. |
| `getBusiness(businessId)` | `GET /api/v1/businesses/{businessId}` | Full `BusinessDto` including the weekly schedule. |
| `listTypes()` | `GET /api/v1/businesses/types` | `BusinessTypeOptionDto` reference data, by label. |

All endpoints require authentication (the app-wide default); none are under `/public/**`.

## Entities

| Entity | Table | Notes |
|---|---|---|
| `BusinessAccountEntity` | `business_accounts` | `location` is `geography(Point,4326)` mapped to a JTS `Point`. `city` is held as a plain id, not an association — `cities` belongs to the profile module. |
| `BusinessHoursEntity` | `business_hours` | One row per (business, weekday). `weekday` is ISO-8601 1–7, matching `java.time.DayOfWeek.getValue()`. |
| `BusinessTypeOptionEntity` | `business_type_options` | Reference data. |

`business_account_verification_status_options` and `business_account_active_status_options` are
**not** mapped. They are internal gating labels that the app never displays, so entities for them
would be dead code; the statuses are compared as strings against the constants on
`BusinessAccountEntity`.

## Cross-module dependencies

None on other feature modules. `business_accounts.city` references the `cities` reference table,
which lives in `shared/geo` (an OPEN module) and is read here directly via `CityRepository`.

The one module dependency is `storage`. `business_accounts.logo_url` stores an R2 **object key**,
not a URL; `BusinessServiceImpl.resolveLogoUrl` turns it into a public URL via
`StorageService.publicUrl(StorageBucket.BUSINESS, key)` before it leaves the module, mirroring how
`ProfileDtoMapper` resolves avatar keys. A value that already starts with `http` is passed through,
so an externally hosted logo still works. **The stored key must include the `business/` prefix**
(`business/{businessId}/Logo.webp`), the same convention avatars use — the public domain is mapped
to the bucket root, so a key without the prefix resolves to a 404.

`cities.id` is a **slug** (`cluj-napoca`), not a display name — `cities.name` holds the properly
accented `Cluj-Napoca`. `BusinessDto` therefore exposes both `city_id` and the resolved `city_name`;
a city id that no longer resolves leaves `city_name` null rather than failing the page.

Coordinate conversion uses `shared.geo.GeoSupport` (PostGIS order is lng, lat).

## Schema notes

The tables were created in Supabase by hand and hardened by the
`business_accounts_schema_hardening` migration:

- `business_hours` had `UNIQUE (business_id)`, permitting only one row per business and so
  contradicting the `weekday` column → replaced with `UNIQUE (business_id, weekday)`.
- `business_hours.business_id` had a stray `DEFAULT gen_random_uuid()` → dropped.
- `weekday` `text` → `smallint` with a 1–7 CHECK; `opening_hour`/`closing_hour` `text` → `time`,
  nullable, plus `is_closed` and a CHECK tying the two together.
- `business_accounts.is_currently_open` dropped (derived instead); `timezone` added.
- `verified_at` made nullable (a pending business has not been verified);
  `average_rating` given `DEFAULT 0`.
- `location` tightened from untyped `geography` to `geography(Point,4326)` and given a GiST index.
- `UNIQUE (website_url)` dropped — two branches of a chain share one website.

## Not built yet

- **Reviews.** `average_rating` and `review_count` exist on the table but no reviews table does;
  nothing maintains them yet.
- **Follows.** `follower_count` likewise has no writer.
- **Business login and writes** — see above.
- **Business posts** (portfolio / previous client work) — the stated intent for business accounts
  once they can log in.
