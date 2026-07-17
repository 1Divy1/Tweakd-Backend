# DMs (Direct Messaging) — Implementation Progress

Tracking file for the DM feature build-out. If a session dies mid-work, resume from the checklist
below — every step is marked as it completes.

## Agreed design (user-approved 2026-07-15)

- **No direct Flutter ↔ Supabase communication.** The Spring backend is the realtime gateway.
- **REST for everything durable** (send, history, conversation list, mark-read, delete, hide) —
  standard module conventions, JWT auth, service-layer checks.
- **STOMP WebSocket at `/ws` for everything live** — new-message delivery, read receipts,
  message-deleted events, typing indicators. Authenticated at the STOMP CONNECT frame with the
  same Supabase JWT used for REST (HTTP handshake itself is `permitAll`; no STOMP session without
  a valid CONNECT). Each user subscribes to their private queue `/user/queue/dms`.
- Typing indicators are the only *upstream* socket traffic (`/app/dms/typing`); ephemeral, never
  persisted, relayed only after verifying the sender is a conversation participant.
- In-memory simple broker — correct for the current single-instance deployment. Horizontal
  scaling later = swap in a broker relay (e.g. Redis/RabbitMQ); REST/DB design unaffected.
- **Scope (user-picked):** plain text messages + typing indicator + **read receipts** +
  **sender-side message delete** (soft; shows "deleted" for both sides). No edits, no image
  attachments in v1.
- **DM policy (user-picked):** anyone can DM anyone (consistent with all-accounts-public).
- 1:1 conversations only (no groups). "Delete chat" from the list = per-user hide
  (`hidden_at`), resurrected when a new message arrives.

## Schema (Supabase migration `create_dms_tables`)

- `dm_conversations` — canonical pair `user_a < user_b` (CHECK + UNIQUE), denormalized
  `last_message_at` / `last_message_preview` / `last_message_sender_id` for cheap list rendering.
- `dm_participant_state` — PK (conversation_id, user_id): `unread_count`,
  `last_read_message_id` (read receipts), `hidden_at`.
- `dm_messages` — app-generated UUID id, `content`, `is_deleted` (content blanked on delete),
  app-written `created_at`; keyset index `(conversation_id, created_at desc, id desc)`.
- RLS enabled, no policies (matches every other table: direct client access blocked; the backend
  connects as the table owner).

## Module layout

- `dms/` public root: `DmsService`, DTOs (`DmConversationDto`, `DmMessageDto`, page DTOs,
  `SendMessageRequest`), exceptions. Peer rendered as `profile.dto.ProfileSearchResultDto`
  (same convention as forums' `author`).
- `dms/internal/`: entities, repositories, `DmsServiceImpl`, `DmsController` (REST),
  `DmsTypingController` (STOMP `@MessageMapping`), `DmEventPusher`
  (`@TransactionalEventListener(AFTER_COMMIT)` → `SimpMessagingTemplate.convertAndSendToUser`).
- `shared/realtime/`: `WebSocketConfig` (`@EnableWebSocketMessageBroker`, `/ws` endpoint,
  `/queue` simple broker, `/app` prefix) + `JwtChannelInterceptor` (CONNECT-frame auth via the
  existing `JwtDecoder` + `JwtAuthenticationConverter`). Cross-cutting on purpose — the
  notification module can later push over the same socket.
- `SecurityConfig`: add `.requestMatchers("/ws/**").permitAll()` (auth happens at CONNECT).
- `pom.xml`: add `spring-boot-starter-websocket`.

## REST endpoints (`/api/v1/dms`)

| Method | Path | Purpose |
|---|---|---|
| GET | `/conversations?cursor=&size=` | Chats list, keyset by last_message_at desc; peer summary, preview, unread count |
| POST | `/conversations/{id}/hide` | Hide chat from my list (peer unaffected) |
| GET | `/conversations/{id}/messages?cursor=&size=` | History, newest first, keyset |
| POST | `/messages` `{recipientId, content}` | Send; creates conversation on first message |
| POST | `/conversations/{id}/read` | Mark read up to latest → read-receipt event to peer |
| DELETE | `/messages/{id}` | Sender soft-deletes own message → event to peer |
| GET | `/unread-count` | Total unread badge |

## WebSocket protocol

- Client connects to `/ws` with `Authorization: Bearer <supabase jwt>` native STOMP header.
- Subscribes `/user/queue/dms`. Event envelope: `{type, conversationId, ...}` with types
  `message.created`, `message.deleted`, `conversation.read`, `typing`.
- Client sends typing to `/app/dms/typing` `{conversationId, typing}`.
- Flutter: `stomp_dart_client` package.

## Checklist

- [x] Plan approved by user (architecture / scope / policy)
- [x] DMS_PROGRESS.md created
- [x] Supabase migration applied (`create_dms_tables`)
- [x] pom.xml: websocket starter
- [x] `shared/realtime` WebSocket config + JWT CONNECT auth + SecurityConfig permit `/ws/**`
- [x] `dms` module: public API (service interface, DTOs, exceptions)
- [x] `dms` module: entities + repositories
- [x] `dms` module: `DmsServiceImpl` (send / list / read / delete / hide / unread / typing check)
- [x] `dms` module: REST controller + typing STOMP controller + `DmEventPusher`
- [x] Docs: `dms/README.md`, CONTEXT.md module list
- [x] Verify: compile OK, `ModularityTests#verifiesModularStructure` green, **app booted against
      live Supabase** (`ddl-auto: validate` accepted all three new entities), smoke checks:
      `GET /api/v1/dms/unread-count` without JWT → 401, `GET /ws` without upgrade → 400 (reachable,
      not 401), WS upgrade handshake → 101.
- [x] Final: update this file, memory

## Status: DONE (2026-07-15)

Backend implementation complete and verified as above. Not yet exercised end-to-end with two real
authenticated users over STOMP (needs two JWTs / the Flutter client) — same "untested vs live
users" caveat as forums had.

## Car tagging (2026-07-17)

Users can tag cars in DM messages (any user's cars, own or others'). The car owner is **not**
tagged and **not** notified — only the cars appear. Backend-only work on the message itself; the
mobile picker reuses the existing profile-search + garage endpoints.

- **Table** `dm_message_car_tags(message_id → dm_messages CASCADE, car_id → cars CASCADE,
  created_at, PK(message_id, car_id))` + index on `car_id`. Already migrated (see
  MINI_FEATURES_PROGRESS migration #3) and in `src/test/resources/db/schema.sql`.
- **`SendMessageRequest`** — `content` is now **optional** (`@Size(max=2000)`, no `@NotBlank`);
  added optional `taggedCarIds` (`List<UUID>`). A message is valid with non-blank text **or** ≥1
  tagged car. Rules (enforced in `DmsServiceImpl`, surfaced as dms exceptions → 400):
  `TooManyTaggedCarsException` (>10), `EmptyMessageException` (neither), `TaggedCarNotFoundException`
  (unknown car). Ids are silently de-duplicated. Blank/null content + cars ⇒ stored `content = ''`.
- **`DmMessageDto`** gained `taggedCars` (`List<CarSummaryDto>`, garage public API — mirrors posts).
  Batch-resolved once per page in `DmsServiceImpl.toDtos` (one tag query + one
  `GarageService.findCarsByIds` per page, no N+1). A **soft-deleted** message returns an empty
  `taggedCars` list (same treatment as its blanked content) and is excluded from the tag lookup.
- **Realtime** — `DmMessageCreatedEvent` already wraps the `DmMessageDto`, so the enriched dto
  (with `taggedCars`) rides the `message.created` socket push automatically.
- **Chats-list preview** — the stored `last_message_preview` is the raw content; a **blank preview
  on a non-deleted latest message** means "shared cars" and the **client** renders that label.
- **Deleted car** ⇒ ON DELETE CASCADE silently removes the tag from old messages.
- **Modulith:** dms → garage (garage does not depend on dms → no cycle;
  `ModularityTests#verifiesModularStructure` green).
- **New files:** `dms/internal/entities/DmMessageCarTagEntity.java` + `DmMessageCarTagId.java`,
  `dms/internal/repositories/DmMessageCarTagRepository.java`, `dms/exception/EmptyMessageException.java`,
  `TooManyTaggedCarsException.java`, `TaggedCarNotFoundException.java`.
  **Modified:** `dms/dto/SendMessageRequest.java`, `dms/dto/DmMessageDto.java`,
  `dms/internal/DmsServiceImpl.java`. **Tests:** new `DmMessageCarTagRepositoryIT`; extended
  `DmsServiceImplTest` / `DmsControllerWebTest` / `DmEventPusherTest`; `src/test/resources/db/seed.sql`
  gained minimal car reference rows.

## Deferred / follow-ups

- **Blocking:** `blocked_accounts` table exists but `relationships` exposes no public block-check
  API. Once it does, add a check in `DmsServiceImpl.sendMessage`.
- Push notifications for offline recipients (no push infra exists yet; offline users see unread
  counts on next app open).
- Reporting DM messages (report module wiring, like posts/forums).
- Multi-instance broker relay if the backend ever scales horizontally.
- Supabase advisory noted 2026-07-15: `public.spatial_ref_sys` (PostGIS system table) has RLS
  disabled — pre-existing, unrelated to DMs, generally left as-is because PostGIS owns it.
