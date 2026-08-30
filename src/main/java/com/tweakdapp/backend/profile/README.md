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
| `completeOnboarding(userId, request)` | Saves name, username, city, discovery radius (required) and bio (optional) after first sign-in |
| `getPublicProfileByUsername(username)` | Returns a public view of any profile by username |
| `searchByUsername(prefix)` | Prefix search across all usernames |
| `findIdByUsername(username)` | Lookup helper for sibling modules — resolves a username to its UUID |
| `findByIds(ids)` | Lookup helper for sibling modules — bulk hydrate a list of UUIDs to search result DTOs |
| `listCountries()` / `listCities(countryId)` | Read-only onboarding reference lists |
| `updateLocation(userId, request)` | Sets city, discovery radius, and/or home-location point |
| `updateRealtimeLocation(userId, request)` | Stores the user's live location after opt-in |
| `getNotificationPreferences(userId)` / `updateNotificationPreferences(userId, request)` | Read (lazily defaults) / replace notification toggles |

### DTOs / records

| Type | Fields | Used for |
|---|---|---|
| `ProfileDto` | id, role, name, username, avatarUrl, bio, externalLink, followersCount, followingCount, isVerified, isBusiness, requiresOnboarding | Own-profile responses |
| `PublicProfileDto` | id, name, username, avatarUrl, bio, externalLink, followersCount, followingCount, isVerified, isBusiness | Public profile view (no `requiresOnboarding`) |
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
| role | String | |
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
