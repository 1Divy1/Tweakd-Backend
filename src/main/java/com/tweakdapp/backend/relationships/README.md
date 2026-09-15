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
| `findFollowingIds(userId)` | Ids the user follows, most recent follow first — for the feed's reposts, so no other module reads `follows` |
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

## Blocking

A block is **two-way in effect**: once either account blocks the other, neither sees the other's
profile, content, or search result, and they cannot message each other. The blocked account is
never notified. Owns `blocked_accounts (blocker_id, blocked_id, created_at)`.

### REST endpoints — base path `/api/v1/blocks`

| Method | Path | Description |
|---|---|---|
| GET | `/` | Accounts the caller blocked, newest first (`BlockedAccountDto`: id, name, username, avatar_url, blocked_at) |
| POST | `/{username}` | Block (204). Idempotent. 400 `CannotBlockSelfException`, 404 unknown username |
| DELETE | `/{username}` | Unblock (204). Idempotent |

`BlockService.block` also deletes follows between the pair **in both directions** (not restored on
unblock) and publishes `shared.blocking.UserBlockedEvent`, on which `notification` deletes the
pair's notifications and `dms` zeroes their conversation's unread counts.

### Enforcement

`BlockDirectoryImpl` implements `shared.blocking.BlockDirectory` (two-way hidden-id lookups). Content
modules read it through `ProfileService.findVisibleIdByUsername` / `findHiddenProfileIds` /
`isHiddenFrom`, so a block reads exactly like a missing account (404) and hidden authors are
filtered inside the paged queries (`not in :hiddenIds`, never-empty via `BlockDirectory.asQueryParam`).
Covered: profile + badges + search, follow status/lists, garage + car detail, tags, reputation,
posts (feed, post, comments, likers, saved, reposts, engagement, tagging), forums, DM list/open/typing/
presence, event attendees and line-up, organizer search, notifications. Contest entries and
leaderboards are deliberately **not** filtered (owner decision: rankings stay the same for everyone).
DM sending and Realtime topic auth are enforced in Supabase (`dm_send_message`, `dm_topic_is_peer`).

