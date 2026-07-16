# dms module

1:1 direct messaging. **The backend is the realtime gateway** — the Flutter client never talks to
Supabase Realtime. Durable operations are REST; live delivery (new messages, read receipts,
deletions, typing) is pushed over the shared STOMP WebSocket (`shared/realtime`, endpoint `/ws`)
to each participant's private queue `/user/queue/dms`.

Anyone can DM anyone (all accounts are public; there is no block check yet — `blocked_accounts`
exists but the `relationships` module exposes no public API for it). Conversations are created
implicitly by the pair's first message. "Delete chat" is a per-user *hide*; a new message from
either side unhides it. Message delete is a soft delete: the row keeps its place, `deleted` flips,
content is blanked, both sides render a placeholder.

## Public API — `DmsService`

| Method | Description |
|---|---|
| `listConversations(userId, cursor, size)` | Chats list, keyset by `last_message_at` desc; peer summary + preview + unread count |
| `listMessages(userId, conversationId, cursor, size)` | One history page, newest first, + peer's read watermark |
| `sendMessage(senderId, request)` | Persist + bump unread + unhide both sides → `message.created` push after commit |
| `markRead(userId, conversationId)` | Zero unread, advance watermark → `conversation.read` push after commit |
| `deleteMessage(userId, messageId)` | Sender-only soft delete (idempotent) → `message.deleted` push after commit |
| `hideConversation(userId, conversationId)` | Hide from caller's list, zero their unread |
| `countUnread(userId)` | Total unread for the app badge |
| `relayTyping(userId, conversationId, typing)` | Ephemeral relay to the peer's queue; silently dropped if not a participant |

## REST endpoints (`/api/v1/dms`)

| Method | Path | Description |
|---|---|---|
| GET | `/conversations?cursor=&size=` | Chats list page (rows carry flat `peer_online` / `peer_last_seen_at`) |
| GET | `/conversations/{id}/messages?cursor=&size=` | History page + `peerLastReadMessageId` |
| POST | `/messages` | `{recipientId, content}` → 201 + the saved message |
| POST | `/conversations/{id}/read` | Mark read → `{conversationId, lastReadMessageId}` |
| POST | `/conversations/{id}/hide` | Hide chat (204) |
| DELETE | `/messages/{id}` | Soft-delete own message (204) |
| GET | `/unread-count` | `{ "unread": n }` |

Not-your-conversation and not-your-message are deliberately the same 404 as not-found.

## WebSocket protocol

1. Connect to `/ws` (raw WebSocket, no SockJS) with STOMP CONNECT header
   `Authorization: Bearer <supabase jwt>` — validated by `shared/realtime/JwtChannelInterceptor`
   with the same decoder as REST. The HTTP handshake itself is `permitAll`.
2. Subscribe to `/user/queue/dms` (subscriptions to anything but own `/user/queue/**` are rejected).
3. Receive `DmSocketEvent` envelopes: `{type, conversationId, ...}` with `type` ∈
   `message.created` | `message.deleted` | `conversation.read` | `typing` | `presence` (see the
   record's javadoc for which fields each type fills).
4. Send typing pings to `/app/dms/typing` as `{conversationId, typing}` (throttle client-side).

Flutter: `stomp_dart_client`. Events are pushed via `@TransactionalEventListener(AFTER_COMMIT)`
(`DmEventPusher`), so a rollback can never announce a phantom message; offline users catch up over
REST. The in-memory simple broker is single-instance by design — swap in a broker relay if the
backend ever scales horizontally.

## Entities

| Entity → table | Notes |
|---|---|
| `DmConversationEntity` → `dm_conversations` | canonical pair `user_a < user_b` (DB CHECK + UNIQUE); denormalized `last_message_at/preview/sender_id` so the list never touches `dm_messages`; preview `null` when the latest message was deleted |
| `DmParticipantStateEntity` → `dm_participant_state` | composite PK (record `@IdClass`); `unread_count`, `last_read_message_id` (read-receipt watermark), `hidden_at` |
| `DmMessageEntity` → `dm_messages` | app-generated UUID + **app-written** `created_at` (so cursor and `last_message_at` agree exactly); `is_deleted` + blanked content on delete |

Conversation + both state rows are created with native `ON CONFLICT DO NOTHING` upserts so two
simultaneous "first messages" cannot fail on the unique constraint.

## Cross-module interactions

- `profile` — `ProfileService.findByIds` hydrates peers (`ProfileSearchResultDto`, same convention
  as forums' `author`) and validates recipients.
- `presence` — `PresenceService.getPresence` hydrates `peer_online` / `peer_last_seen_at` on the
  chats list; `DmPresencePusher` listens for `UserPresenceChangedEvent` and pushes the `presence`
  socket event to everyone the user shares a conversation with.
- `shared/realtime` — WebSocket infrastructure (config + CONNECT-frame JWT auth), usable by other
  modules later (e.g. notifications).

## Deferred

- Block enforcement on send (needs a public block-check on `relationships`).
- Push notifications for offline recipients (no push infra exists).
- Reporting DM messages.
