# support module

Support tickets from users and business accounts. Owns `support_tickets`,
`support_ticket_messages`, and the seeded `support_ticket_categories`. Depends on `profile` (to
hydrate requester/assignee cards and the business badge) and `shared`.

**Status model:** `open` = waiting on staff, `awaiting_user` = staff replied last, `resolved` =
closed. A user reply always moves the ticket (back) to `open` — replying to a resolved ticket
reopens it and clears `resolved_at`; a staff reply moves it to `awaiting_user`. **Priority**
(low/normal/high/urgent) is staff-set only. `last_message_at` / `updated_at` are bumped by a
Supabase trigger on message insert.

The staff methods perform **no authorization** — the `admin` module gates them behind its
team-capability checks (`ANSWER_TICKETS`) and owns the staff REST endpoints under
`/api/v1/admin/support/**`. Staff-reply notifications are also the admin module's job, so this
module does not depend on `notification`.

## Public API — `SupportService`

| Method | Description |
|---|---|
| `listCategories()` | Ticket categories for the picker |
| `createTicket(userId, request)` | Open a ticket with its first message |
| `listMyTickets(userId, cursor, size)` | The caller's tickets, most recently active first (keyset) |
| `getTicket(requesterId, ticketId)` | Owner-scoped detail; someone else's ticket is an indistinguishable 404 |
| `addMessage(userId, ticketId, request)` | User reply → status `open` |
| `listTickets(status, cursor, size)` | *(staff)* queue, optional status filter |
| `getTicketAsStaff(ticketId)` | *(staff)* any ticket's conversation |
| `addStaffMessage(staffId, ticketId, request)` | *(staff)* reply → `awaiting_user` |
| `updateTicket(ticketId, request)` | *(staff)* triage: priority / status / assignee (`unassign` flag) |
| `getStats()` | open / awaiting_user / resolved-today counts |

## REST endpoints (user-facing)

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/support/categories` | List categories |
| POST | `/api/v1/support/tickets` | Open a ticket → `201` |
| GET | `/api/v1/support/tickets/mine?cursor=&size=` | The caller's tickets |
| GET | `/api/v1/support/tickets/{id}` | One ticket + conversation (owner only) |
| POST | `/api/v1/support/tickets/{id}/messages` | Reply (reopens if resolved) |

## Entities

| Entity → table | Notes |
|---|---|
| `SupportTicketEntity` → `support_tickets` | `priority`/`status` insertable=false (DB defaults `normal`/`open`); `resolved_at` set/cleared by the service; `created_at`/`updated_at`/`last_message_at` DB-managed |
| `SupportTicketMessageEntity` → `support_ticket_messages` | `is_staff` marks staff replies |
| `TicketCategoryOptionEntity` → `support_ticket_categories` | Seeded: account, billing, technical, content, other |
