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