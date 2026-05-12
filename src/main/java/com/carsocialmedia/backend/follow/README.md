# follow module

Manages follow relationships between users. Owns the `follows` table. Depends on the `profile` module for username resolution and privacy checks.

## Public API

### Service interface — `FollowService`

| Method | Description |
|---|---|
| `follow(currentUserId, targetUsername)` | Create a follow row; status set by Supabase trigger (`accepted` or `pending`) |
| `unfollow(currentUserId, targetUsername)` | Delete follow row (accepted or pending). Idempotent |
| `getFollowStatus(currentUserId, targetUsername)` | Current relationship from caller toward target |
| `getPendingRequests(currentUserId)` | Pending follow requests received by the caller |
| `acceptRequest(currentUserId, requesterUsername)` | Accept a pending request |
| `rejectRequest(currentUserId, requesterUsername)` | Reject (delete) a pending request |
| `getFollowers(currentUserId, targetUsername)` | Accepted followers of target (privacy-gated) |
| `getFollowing(currentUserId, targetUsername)` | Users that target follows (privacy-gated) |
| `isAcceptedFollower(viewerId, targetId)` | Whether the viewer is an accepted follower of the target. Sibling-module hook (used by `garage` to gate private garages) |

### DTOs / records

| Type | Fields | Used for |
|---|---|---|
| `FollowStatusDto` | status (`FollowStatus`) | Follow / unfollow / status responses |
| `FollowRequestDto` | id, username, avatarUrl, createdAt | Pending request list |

### Enums

`FollowStatus` — `ACCEPTED`, `PENDING`, `NOT_FOLLOWING`

### Exceptions

| Exception | HTTP | Trigger |
|---|---|---|
| `CannotFollowSelfException` | 400 | Caller tries to follow their own account |
| `PrivateProfileException` | 403 | Caller tries to view a private social graph without being an accepted follower |
| `FollowRequestNotFoundException` | 404 | Accept / reject called for a request that doesn't exist |

## REST endpoints

Base path: `/api/v1/follow`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/{username}` | required | Follow a user |
| DELETE | `/{username}` | required | Unfollow a user (204) |
| GET | `/{username}/status` | required | Relationship status toward a user |
| GET | `/requests` | required | Incoming pending follow requests |
| POST | `/requests/{username}/accept` | required | Accept a pending request (204) |
| DELETE | `/requests/{username}` | required | Reject a pending request (204) |
| GET | `/{username}/followers` | required | Follower list (privacy-gated) |
| GET | `/{username}/following` | required | Following list (privacy-gated) |

## Entity — `FollowEntity` → table `follows`

| Column | Type | Notes |
|---|---|---|
| followerId | UUID | Part of composite PK (`FollowId`) |
| followingId | UUID | Part of composite PK (`FollowId`) |
| status | String | `accepted` or `pending`; set by `set_follow_initial_status` trigger on INSERT |
| createdAt | Instant | DB default `now()`; `insertable=false, updatable=false` |

The composite key is modelled as `@EmbeddedId FollowId`.

## Supabase triggers

| Trigger | Table event | Effect |
|---|---|---|
| `set_follow_initial_status` | BEFORE INSERT on `follows` | Sets `status` to `accepted` (public target) or `pending` (private target) |
| `handle_follow_change` | AFTER UPDATE on `follows` (pending → accepted) | Increments `followersCount` / `followingCount` on both profiles |

Because the INSERT trigger sets `status`, the Java layer intentionally leaves the field null before saving. After `saveAndFlush`, `EntityManager.refresh()` is called to read the trigger-assigned value back from the database.

## Cross-module dependencies

- **`profile.ProfileService`** — used for username-to-UUID resolution (`findIdByUsername`), privacy flag checks (`isPrivate`), and bulk profile hydration (`findByIds`).
- **`profile.ProfileBecamePublicEvent`** — consumed by `FollowEventListener`: when a private account goes public, all pending requests addressed to it are bulk-accepted (counters updated by the `handle_follow_change` trigger per row).
- **`profile.ProfileSearchResultDto`** — used in followers / following list responses and pending-request hydration.
- **`profile.exception.ProfileNotFoundException`** — thrown by this module when a target username cannot be resolved.
