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

## Staff directory — `staff/StaffDirectory`

Staff (admin-dashboard) accounts are separate from app accounts — profile-less Supabase auth users
whose identity lives on `admin_team_members`. `StaffDirectory` (implemented by the `admin` module)
resolves staff UUIDs to `StaffRefDto` (id, display name, avatar) so lower-level modules like
`support` can render staff actors without depending on `admin` (which would be a cycle — `admin`
orchestrates them).

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
