# presence module

Online / "Active now" / last-seen status. **Source of truth is the shared STOMP WebSocket
(`shared/realtime`, endpoint `/ws`)**: a user is *online* while they hold ≥1 authenticated socket
session (multi-device counted). The Flutter app connects the socket at app start, so "Active now"
means "has the app open". Presence is visible to any authenticated user (all accounts are public).

## How it works (and why it's cheap)

- `PresenceRegistry` (in-memory, synchronized maps) tracks user → session-id set. Fed by the
  broker's `SessionConnectedEvent` / `SessionDisconnectEvent` (`PresenceSessionListener`).
- **Offline grace window (20s, `PresenceLifecycle.OFFLINE_GRACE`)**: when a user's last session
  closes they enter "pending offline"; a `@Scheduled` sweep (10s) finalizes them only if they have
  not reconnected. A mobile network flap therefore causes **zero** DB writes and zero fan-out —
  publicly nothing happened.
- On real transitions, `PresenceLifecycle` upserts `user_presence.last_seen_at` and publishes
  `UserPresenceChangedEvent` (in-JVM application event) for other modules to react to — currently
  `dms` fans it out to conversation peers (`DmPresencePusher`).
- A second `@Scheduled` job (2min) batch-touches `last_seen_at` for everyone online, so a server
  restart (which wipes the registry) leaves last-seens at most ~2min stale instead of hours.
- The upsert is FK-safe (`insert … select from profiles … on conflict do update`): staff accounts
  can authenticate to `/ws` but have no `profiles` row — for them it silently no-ops.

Load profile: no recurring per-user traffic. Connect/disconnect bump an in-memory map; the DB is
touched only on debounced transitions plus the cheap batch flush; events fan out only on
transitions. Online state is in-memory only — consistent with the single-instance simple broker; a
shared registry (e.g. Redis) is the multi-instance path, together with the broker relay.

`PresenceConfig` carries the codebase's first `@EnableScheduling`.

## Public API — `PresenceService`

| Method | Description |
|---|---|
| `getPresence(userIds)` | Batch lookup → `Map<UUID, PresenceDto>`; every requested id gets an entry |

`PresenceDto(userId, online, lastSeenAt)` — online ⇒ `lastSeenAt == null`; offline with
`lastSeenAt == null` ⇒ never seen online.

`UserPresenceChangedEvent(userId, online, lastSeenAt)` — published on public transitions only.

## REST endpoint (`/api/v1/presence`)

| Method | Path | Description |
|---|---|---|
| GET | `?user_ids=a,b,c` | Batch presence (max 100 ids, else 400); returns `[{user_id, online, last_seen_at}]` |

## Entities

| Entity → table | Notes |
|---|---|
| `UserPresenceEntity` → `user_presence` | `user_id` PK → `profiles(id)` ON DELETE CASCADE; `last_seen_at` NOT NULL. Deliberately its own tiny table so presence churn never touches hot `profiles` rows. RLS enabled, no policies (backend-only) |

## Cross-module interactions

- `shared/realtime` — the socket whose session lifecycle drives everything.
- `dms` — depends on presence (never the reverse): hydrates `peer_online` / `peer_last_seen_at` on
  the chats list and pushes the `presence` socket event to conversation peers.
