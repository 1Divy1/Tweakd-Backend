# relationships module

Manages follow relationships between users. Owns the `follows` table. Depends on the `profile`
module for username resolution and profile hydration.

All accounts are public, so a follow takes effect immediately — there is no request/approval flow.

## Public API

### Service interface — `RelationshipService`

| Method | Description |
|---|---|
| `follow(currentUserId, targetUsername)` | Create a follow row (status `accepted`). Idempotent |
| `unfollow(currentUserId, targetUsername)` | Delete the follow row. Idempotent |
| `getFollowStatus(currentUserId, targetUsername)` | Current relationship from caller toward target |
| `getFollowers(currentUserId, targetUsername)` | Followers of target |
| `getFollowing(currentUserId, targetUsername)` | Users that target follows |
| `removeFollower(currentUserId, followerUsername)` | Remove someone from the caller's followers |

### DTOs / records

| Type | Fields | Used for |
|---|---|---|
| `FollowStatusDto` | status (`FollowStatus`) | Follow / unfollow / status responses |
| `FollowProfileSearchResult` | id, username, avatarUrl, isFollowing | Followers / following list entries |

### Enums

`FollowStatus` — `ACCEPTED`, `NOT_FOLLOWING`

### Exceptions

| Exception | HTTP | Trigger |
|---|---|---|
| `CannotFollowSelfException` | 400 | Caller tries to follow their own account |

## REST endpoints

Base path: `/api/v1/follow`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/{username}` | required | Follow a user |
| DELETE | `/{username}` | required | Unfollow a user (204) |
| GET | `/{username}/status` | required | Relationship status toward a user |
| DELETE | `/followers/{username}` | required | Remove a follower (204) |
| GET | `/{username}/followers` | required | Follower list |
| GET | `/{username}/following` | required | Following list |

## Entity — `RelationshipEntity` → table `follows`

| Column | Type | Notes |
|---|---|---|
| followerId | UUID | Part of composite PK (`RelationshipId`) |
| followingId | UUID | Part of composite PK (`RelationshipId`) |
| status | String | Always `accepted`; also forced by `set_follow_initial_status` trigger on INSERT |
| createdAt | Instant | DB default `now()`; `insertable=false, updatable=false` |

The composite key is modelled as `@EmbeddedId RelationshipId`.

## Supabase triggers

| Trigger | Table event | Effect |
|---|---|---|
| `set_follow_initial_status` | BEFORE INSERT on `follows` | Sets `status` to `accepted` (all accounts are public) |
| `handle_follow_change` | AFTER INSERT / UPDATE / DELETE on `follows` | Maintains `followersCount` / `followingCount` on both profiles |

## Cross-module dependencies

- **`profile.ProfileService`** — username-to-UUID resolution (`findIdByUsername`) and bulk profile
  hydration (`findByIds`).
- **`profile.ProfileSearchResultDto`** — used in followers / following list responses.
- **`profile.exception.ProfileNotFoundException`** — thrown when a target username cannot be resolved.
