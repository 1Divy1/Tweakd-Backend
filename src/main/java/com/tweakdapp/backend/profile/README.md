# profile module

Manages user profile data and onboarding. Owns the `profiles` table plus the
`notification_preferences` table.

> **countries / cities** used to live here, because onboarding was the first feature to need a
> city list. They are app-wide geo reference data, not profile data — once the `business` module
> needed the same tables they moved to [`shared/geo`](../shared/geo). The onboarding endpoints
> below are unchanged; only the entity/DTO packages moved.

> **dream_cars** is intentionally *not* here — it references `car_brands`/`car_models`
> (owned by the garage module), so it will live in the **garage** module when built.

## Public API

### Service interface — `ProfileService`

| Method | Description |
|---|---|
| `getProfile(userId)` | Returns the authenticated user's own profile |
| `completeOnboarding(userId, request)` | Saves name, username, city, discovery radius (required) and bio (optional) after first sign-in. Also reports `BadgeTrigger.ACCOUNT_CREATED` to `badges` — the backend's only signal that a new member exists, since signup happens in Supabase's `handle_new_user` trigger. Names no badge: the catalogue decides what a signup is worth, and the account's `created_at` is what its offer window is judged against |
| `getPublicProfileByUsername(username)` | Returns a public view of any profile by username |
| `getBadgesByUsername(username)` | Just the badge row of a profile — the same list the profile already carries, for refetching it alone |
| `searchByUsername(prefix)` | Prefix search across all usernames |
| `findIdByUsername(username)` | Lookup helper for sibling modules — resolves a username to its UUID |
| `findByIds(ids)` | Lookup helper for sibling modules — bulk hydrate a list of UUIDs to search result DTOs |
| `listCountries()` / `listCities(countryId)` | Read-only onboarding reference lists |
| `updateLocation(userId, request)` | Sets city, discovery radius, and/or home-location point |
| `updateRealtimeLocation(userId, request)` | Stores the user's live location after opt-in |
| `getNotificationPreferences(userId)` / `updateNotificationPreferences(userId, request)` | Read (lazily defaults) / replace notification toggles |
| `applyReputationDelta(userId, delta)` | Moves `reputation_score` under a row lock and reports the before/after pair. Called only by the `reputation` module |
| `findReputationScore(userId)` | The current score, or empty if no such profile |

### DTOs / records

| Type | Fields | Used for |
|---|---|---|
| `ProfileDto` | id, name, username, avatarUrl, bio, externalLink, followersCount, followingCount, isVerified, isBusiness, requiresOnboarding, reputationScore | Own-profile responses |
| `PublicProfileDto` | id, name, username, avatarUrl, bio, externalLink, followersCount, followingCount, isVerified, isBusiness, reputationScore | Public profile view (no `requiresOnboarding`) |
| `ProfileSearchResultDto` | id, name, username, avatarUrl | Search results and cross-module hydration |
| `OnboardingRequest` | name, username, cityId, discoveryRadiusKm (required), bio (optional) | POST /onboarding body |
| `LocationRequest` | cityId, discoveryRadiusKm (all optional) | PATCH /me/location body |
| `RealtimeLocationRequest` | lat, lng (required) | PATCH /me/realtime-location body |
| `NotificationPreferencesRequest` | 9 boolean toggles (all required, incl. `tags_enabled`, `service_reminders_enabled`) | PUT /me/notifications body |
| `CountryDto` / `CityDto` | reference data (CityDto exposes lat/lng); defined in `shared/geo` | reference reads |
| `NotificationPreferencesDto` | 9 boolean toggles (`tags_enabled` gates forum tag notifications; `service_reminders_enabled` gates scheduled-service/expiring-document reminders; `event_organizer_enabled` gates organizer-facing map-event notifications) | notification reads/writes |

`ProfileDto` additionally carries `cityId` and `discoveryRadiusKm`.

### Exceptions

| Exception | HTTP | Trigger |
|---|---|---|
| `ProfileNotFoundException` | 404 | Profile row not found by id or username |
| `UsernameAlreadyTakenException` | 409 | Username already exists in `profiles` |

## REST endpoints

Base path: `/api/v1/profile`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/me` | required | Own profile |
| POST | `/onboarding` | required | Complete onboarding (set username / bio) |
| GET | `/by-username/{username}` | required | Public view of any profile |
| GET | `/by-username/{username}/badges` | required | Just that profile's badge row |
| GET | `/search?q={prefix}` | required | Username prefix search |
| PATCH | `/me/location` | required | Update city / radius |
| PATCH | `/me/realtime-location` | required | Push live location after opt-in |
| GET / PUT | `/me/notifications` | required | Read / replace notification toggles |

Reference reads (base path `/api/v1/profile/reference`):

| Method | Path | Description |
|---|---|---|
| GET | `/countries` | All countries |
| GET | `/countries/{countryId}/cities` | Cities in a country |

## Entity — `ProfileEntity` → table `profiles`

| Column | Type | Notes |
|---|---|---|
| id | UUID | PK, matches Supabase auth user id |
| name | String | |
| username | String | Unique |
| avatarUrl | String | |
| bio | String | |
| externalLink | String | |
| followersCount | int | Managed by Supabase triggers |
| followingCount | int | Managed by Supabase triggers |
| isVerified | boolean | |
| isBusiness | boolean | |
| requiresOnboarding | boolean | |
| reputationScore | int | Community reputation. Moved only via `applyReputationDelta`; the `reputation` module owns the history behind it |
| city | CityEntity (`shared.geo`) | `@ManyToOne` on `city_id`, nullable |
| discoveryRadiusKm | Integer | nullable, DB check 1–100 |
| realtimeLocation | Point | `geography` (unconstrained), nullable |

`@DynamicUpdate` is set so only changed columns are sent in UPDATE statements.

### Other entities

`CountryEntity` / `CityEntity` now live in `shared/geo`. `NotificationPreferencesEntity` is 1:1 with a profile
(`profile_id` PK).

### Geography

Location columns are mapped with **JTS `Point`** via `hibernate-spatial`
(`@JdbcTypeCode(GEOGRAPHY)`, SRID 4326). The API speaks plain lat/lng doubles;
`GeoSupport` (profile/internal) converts to/from `Point` (coordinate order is
**lng, lat**). This stores location as first-class, queryable data for future
proximity-based features.

> **Pending Supabase migrations** (flagged, not applied): constrain
> `profiles.realtime_location` to `geography(Point,4326)`, and set `cities.region`
> NOT NULL to match the entity (`region` is mapped `nullable = false`).

## Reputation

`profiles.reputation_score` lives here, but only the number. The itemised history behind it
(`reputation_score_history`) and the reason catalogue belong to the
[`reputation` module](../reputation/README.md), which is the only caller of
`applyReputationDelta` — it moves the score under a pessimistic write lock and records the
before/after pair on its history row in the same transaction. Nothing else, including any trigger,
writes this column.

`applyReputationDelta` also serves revocation, called with a negated delta. It clamps at zero in
both directions, so a heavily penalised account bottoms out rather than going negative.

## Supabase triggers

Counter columns (`followersCount`, `followingCount`) are maintained by triggers in Supabase. The Java layer never writes them directly.

`notification_preferences` flags default to `true` at the DB level; the service also
inserts a default row during onboarding (and lazily on first read) so the app always
has a row to read.

## Moderation & bans (admin module)

- `getModerationSnapshot(profileId)` → `ProfileModerationSnapshotDto` (identity, followers,
  business flag, ban state, account age) — the case detail's author panel.
- `banUser(profileId, until)` / `unbanUser(profileId)` — `until = null` means permanent.
- Enforcement: `profiles.is_banned` + `banned_until`, checked on **every request** by
  `BannedUserInterceptor` (an MVC interceptor, so it runs after the security chain) through
  `BanCache` (60s TTL, evicted on ban/unban) → 403 "Your account has been banned". An expired temp
  ban reads as not banned. `profiles.created_at` was backfilled from `auth.users` for account age.


## Badges on a profile

`ProfileDto` and `PublicProfileDto` both carry a `badges` array — the profile owner's unlocked
badges, newest first, resolved artwork URLs and all. It is embedded rather than fetched separately
so the profile screen paints its badge row in the same round trip as the header, the way Instagram
highlights sit under the bio. Empty, never null; every path that returns a profile populates it,
including the write paths, so the field is never conditionally present.

That is why this module **depends on `badges` and not the other way round**. `badges` reads nothing
from `profiles` — it has no username-keyed endpoint and no profile existence check — precisely so
this dependency can exist without Modulith seeing a cycle. Username resolution for badges therefore
lives here: `getBadgesByUsername` is the refetch path, for refreshing the row after an unlock
animation without re-pulling the profile.

Badges a user has *not* earned are not here. That list is the user's own business and is served by
`GET /api/v1/badges/me/locked`; it grows with the catalogue and is only wanted when that section of
their own profile is opened. See the [`badges` module](../badges/README.md).
