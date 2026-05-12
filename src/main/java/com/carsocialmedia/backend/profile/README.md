# profile module

Manages user profile data. Owns the `profiles` table.

## Public API

### Service interface — `ProfileService`

| Method | Description |
|---|---|
| `getProfile(userId)` | Returns the authenticated user's own profile |
| `completeOnboarding(userId, request)` | Saves username (required) and bio (optional) after first sign-in |
| `setPrivacy(userId, isPrivate)` | Toggles the account between public and private |
| `getPublicProfileByUsername(username)` | Returns a public view of any profile by username |
| `searchByUsername(prefix)` | Prefix search across all usernames |
| `findIdByUsername(username)` | Lookup helper for sibling modules — resolves a username to its UUID |
| `isPrivate(userId)` | Lookup helper for sibling modules — checks privacy flag without exposing the entity |
| `findByIds(ids)` | Lookup helper for sibling modules — bulk hydrate a list of UUIDs to search result DTOs |

### DTOs / records

| Type | Fields | Used for |
|---|---|---|
| `ProfileDto` | id, role, name, username, avatarUrl, bio, externalLink, followersCount, followingCount, isVerified, isBusiness, isPrivate, requiresOnboarding | Own-profile responses |
| `PublicProfileDto` | id, name, username, avatarUrl, bio, externalLink, followersCount, followingCount, isVerified, isBusiness, isPrivate | Public profile view (no `requiresOnboarding`) |
| `ProfileSearchResultDto` | id, username, avatarUrl | Search results and cross-module hydration |
| `OnboardingRequest` | username (required), bio (optional) | POST /onboarding body |
| `PrivacyRequest` | isPrivate | PATCH /me/privacy body |

### Domain events

| Event | Published when | Consumer |
|---|---|---|
| `ProfileBecamePublicEvent(userId)` | A private account switches to public | `follow` module auto-accepts all pending requests |

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
| PATCH | `/me/privacy` | required | Toggle public / private |
| GET | `/by-username/{username}` | required | Public view of any profile |
| GET | `/search?q={prefix}` | required | Username prefix search |

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
| isPrivate | boolean | |
| requiresOnboarding | boolean | |

`@DynamicUpdate` is set so only changed columns are sent in UPDATE statements.

## Supabase triggers

Counter columns (`followersCount`, `followingCount`) are maintained by triggers in Supabase. The Java layer never writes them directly.
