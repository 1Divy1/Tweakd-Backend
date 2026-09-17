# shared module

Cross-cutting infrastructure available to all modules. Declared `ApplicationModule.Type.OPEN` — every module may import anything from it. Keep additions here minimal and genuinely cross-cutting.

## Security — `SecurityConfig`

Configures stateless JWT authentication using Supabase as an OAuth2 resource server.

- All endpoints require authentication except `/public/**`.
- `/admin/**` requires `ROLE_ADMIN`.
- Roles are extracted from the JWT claim `app_metadata.role` and prefixed with `ROLE_`. Missing claim → `ROLE_USER`.
- `@EnableMethodSecurity` is active, so `@PreAuthorize` works on service and controller methods.
- In controllers, get the authenticated user's Supabase UUID with:
  ```java
  @AuthenticationPrincipal Jwt jwt
  jwt.getSubject()  // UUID string, PK of the profiles table
  ```

## Origin guard — `security/OriginSecretFilter`

Clients reach the API at `api.tweakdapp.com`: a **proxied** (orange-cloud) Cloudflare CNAME to a
Cloud Run domain mapping. A Cloudflare request header Transform Rule adds `X-Origin-Secret` to
every request. The service's `*.run.app` URL is still public, so this filter returns a bare `403`
for any request without that header. Without it, callers could skip Cloudflare's DDoS
protection by calling Cloud Run directly.

- **Order**: a servlet filter at `HIGHEST_PRECEDENCE` (`OriginGuardConfig`), ahead of Spring
  Security. It covers everything, `/ws` and `/public/**` included, and a refused request costs no
  JWT decoding, no ban lookup and no rate-limit token.
- **Config**: `origin-guard.secret` ← `ORIGIN_SECRET` env var. **Unset = guard off**, with a
  startup warning. That's the setting for local runs and tests, and for the rollout window before
  every client has moved off `run.app`. Controller slice tests never load it.
- **Callers that don't go through the Transform Rule** need to send the header themselves, e.g.
  the `web.tweakdapp.com` Worker (from a Wrangler secret). The same goes for any future Cloud Run
  HTTP health check (those come from Google, not Cloudflare), which would need its path exempted.
- **Rotation**: set the new value on the Cloud Run service and in the Cloudflare rule (plus any
  caller that sends it itself) at about the same time. Requests in between get `403`s, so do it at
  a quiet hour.

## Rate limiting — `ratelimit/`

Token-bucket rate limiting, mounted as an MVC interceptor on `/api/**` and `/public/**` (ordered
after `BannedUserInterceptor`, so a banned account is told it is banned rather than told to slow
down).

- **Who is counted**: the JWT subject, or the client IP for unauthenticated `/public/**` traffic
  (`ClientIpResolver` — the *last* `X-Forwarded-For` entry, because a client can forge the first).
- **What applies**: every request consumes from the caller's `general` bucket; a handler (or
  controller class) annotated `@RateLimited(RateLimits.X)` also consumes from that named bucket.
- **Where the numbers live**: `RateLimitProperties`. Defaults are in Java so tests and production
  behave alike; `application.yaml` overrides by name. The annotation carries only a name, so tuning
  never touches controllers.
- **Mode**: `ENFORCE` in production, overridable per environment via the `RATE_LIMIT_MODE` env var
  — `OFF` (kill switch; also what a load test needs, and what local `.env` sets) or `LOG_ONLY`
  (counts and logs, blocks nothing). The Java default stays `LOG_ONLY` so controller slice tests
  are never blocked by a limit they didn't set.
- **Storage**: in-memory Caffeine cache of Bucket4j buckets, per instance, with idle and size
  eviction. Not exact across instances by design; see `RATE_LIMITING_PROGRESS.md`.

Adding a write endpoint? Annotate it with the closest existing `RateLimits` constant.
`RateLimitConfigIT` fails if an annotation names a limit that has no configuration.

## Staff directory — `staff/StaffDirectory`

Staff (admin-dashboard) accounts are separate from app accounts — profile-less Supabase auth users
whose identity lives on `admin_team_members`. `StaffDirectory` (implemented by the `admin` module)
resolves staff UUIDs to `StaffRefDto` (id, display name, avatar) so lower-level modules like
`support` can render staff actors without depending on `admin` (which would be a cycle — `admin`
orchestrates them).

## Blocking — `blocking/`

`BlockDirectory` (implemented by `relationships`, owner of `blocked_accounts`) answers two-way
"is this account hidden from the viewer" lookups for every module without a dependency on
`relationships`. `UserBlockedEvent` is published after a new block. See the relationships README.

## Exception hierarchy

All domain exceptions extend `ApiException`, which carries an `HttpStatus`. `GlobalExceptionHandler` translates them to `ErrorResponse` JSON automatically.

```
ApiException (abstract, carries HttpStatus)
├── BadRequestException   → 400
├── ConflictException     → 409
├── ForbiddenException    → 403
└── NotFoundException     → 404
```

Module-level exceptions (e.g. `ProfileNotFoundException`, `CannotFollowSelfException`) extend the appropriate base class from this hierarchy.

## Error response shape — `ErrorResponse`

```json
{
  "status": 404,
  "message": "Profile not found",
  "errors": { "field": "message" }   // only present for validation failures
}
```

`GlobalExceptionHandler` handles:
- `ApiException` subclasses → status + message from the exception
- `MethodArgumentNotValidException` → 400 with a `field → message` map in `errors`
## Geo reference data — `geo/`

The app-wide `countries` and `cities` lookup tables, plus `GeoSupport` for converting between
plain lat/lng doubles (the wire format) and the JTS `Point`s stored in `geography(Point,4326)`
columns.

| Type | Purpose |
|---|---|
| `CountryEntity` / `CountryRepository` | `countries` reference table |
| `CityEntity` / `CityRepository` | `cities` reference table; carries a `geography(Point,4326)` location |
| `CountryDto` / `CityDto` | wire shapes (`CityDto` exposes lat/lng as doubles) |
| `GeoSupport` | `point(lat, lng)`, `latOf(p)`, `lngOf(p)` — **PostGIS order is (lng, lat)** |

These originally lived in the `profile` module because onboarding was the first feature to need a
city list. They moved here once `business` needed the same tables: a business's city has nothing to
do with a user profile, and one feature module should not have to expose a lookup on behalf of
another. Rows are seeded and owned by Supabase; nothing in the app writes to them.

**`cities.id` is a slug** (`cluj-napoca`, `bucharest`) and is not displayable — `cities.name` holds
the properly accented display name (`Cluj-Napoca`, `București`). Any module that stores a city id
and shows it to a user must resolve the name through `CityRepository`.
