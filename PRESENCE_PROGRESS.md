# Presence (Active now / last seen) — Implementation Progress

Tracking file for the presence feature build-out. If a session dies mid-work, resume from the
checklist below — every step is marked as it completes.

## Agreed design (user-approved 2026-07-15)

- **Visibility: anyone.** Any authenticated user can query any user's presence (consistent with
  all-accounts-public; enables future profile-page use). Live *push* events still only go to DM
  conversation peers.
- **Source of truth: the existing `/ws` STOMP socket.** A user is *online* while they have ≥1
  authenticated WebSocket session (multi-device counted in memory). The Flutter app should connect
  the socket at app start, so "Active now" = "has the app open".
- **Offline grace period (~20s).** A user only transitions offline after their last session has
  been gone for the grace window. Mobile network flaps (reconnects) therefore cause **zero** DB
  writes and zero event fan-out — addresses the user's server-load concern.
- **`last_seen_at` persisted** in a new `user_presence` table (separate from `profiles` so frequent
  presence writes never touch hot profile rows). Written on online-transition, offline-transition,
  and via a periodic flush (~2 min) for long-lived sessions so a server restart can't produce
  hours-stale last-seens. Online/offline state itself is in-memory only — consistent with the
  single-instance simple broker; a broker relay + shared registry is the multi-instance path.
- New small **`presence` module**; `dms` depends on it (never the reverse):
  - public: `PresenceService` (batch lookup), `PresenceDto`, `UserPresenceChangedEvent`
    (in-JVM application event on online/offline transitions).
  - internal: session registry, WS `SessionConnectedEvent`/`SessionDisconnectEvent` listeners,
    grace-period sweep + last-seen flush (`@Scheduled`; `@EnableScheduling` added here — first use
    in the codebase), `user_presence` entity/repo (native upsert), REST controller.
- **DMs integration:**
  - `DmConversationDto` gains flat `peerOnline` / `peerLastSeenAt` (garage flat-field convention)
    → chats list renders dots + the "Active Now" row with no extra calls.
  - `DmSocketEvent` gains type `presence` (`userId`, `online`, `lastSeenAt`), pushed to the user's
    conversation peers on transitions → chat header flips "Active now" ⇄ "Last seen…" live.
  - `GET /api/v1/presence?user_ids=a,b,c` (presence module) for the chat-screen header on open /
    future profile pages. Max 100 ids per call.
- Verified badge in the mockups: **design-only, not built** (no verified accounts exist).
- Server-load profile (user concern): presence adds no recurring per-user traffic — an in-memory
  counter bump on connect/disconnect, DB writes only on debounced transitions + a cheap periodic
  batch flush, fan-out only on transitions.

## Schema (Supabase migration `create_user_presence`)

- `user_presence` — `user_id uuid PK REFERENCES profiles(id) ON DELETE CASCADE`,
  `last_seen_at timestamptz NOT NULL`. RLS enabled, no policies (backend-only access).

## Checklist

- [x] Plan approved by user (visibility = anyone / approach OK, mind server load)
- [x] PRESENCE_PROGRESS.md created
- [x] Supabase migration applied (`create_user_presence`)
- [x] `presence` module: public API (`PresenceService`, `PresenceDto`, `UserPresenceChangedEvent`)
- [x] `presence` module: internal (registry + grace sweep + flush, WS event listeners, entity,
      repo, controller, `@EnableScheduling` config)
- [x] `dms` integration: DTO fields + hydration, `presence` socket event, `DmPresencePusher`,
      peer-ids repo query
- [x] Docs: `presence/README.md`, dms README update, CONTEXT.md module list
- [x] Verify: compile, ModularityTests, boot against live Supabase (ddl validate — passed,
      "Started BackendApplication"), full test suite (3/3 green)
- [x] Final: update this file, memory, deliver snake_case contract delta to user

## Status: DONE (2026-07-16)

Endpoints are implemented and boot-verified but not yet exercised against a real client — same
caveat as the DMs endpoints.
