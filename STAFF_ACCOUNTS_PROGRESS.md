# Staff accounts decoupling — progress

Goal: admin/staff accounts become separate from in-app accounts. Owner-confirmed design
(2026-07-12): staff = **profile-less Supabase auth users** (same auth pool, same JWT chain, but the
`handle_new_user` trigger skips profile creation for them); team members are added by **email
invite** via the Supabase Auth Admin API (backend sets `app_metadata.role = 'admin'` at invite);
`admin_team_members` becomes the staff profile itself (`email`, `display_name`, `avatar_url`);
staff-reference columns lose their FKs to `profiles`.

Env (already in `.env`): `SUPABASE_URL`, `SUPABASE_SECRET_KEY` (service-role key).

Data check (2026-07-12): `admin_team_members`, `moderation_actions`, resolved cases, assigned
tickets, staff messages are ALL EMPTY → no owner-row migration, no history remap needed. Owner
gets created via the new flow + one seed SQL (see Setup below).

## Checklist

- [x] 1. Live schema inspected. Profile FKs found: `admin_team_members.user_id` (CASCADE) +
      `invited_by` (SET NULL), `support_tickets.assigned_to`, `support_ticket_messages.sender_id`,
      `moderation_cases.resolved_by`, `moderation_actions.moderator_id`.
      Trigger `public.handle_new_user` inserts into profiles for every auth user.
- [x] 2. Migrations — APPLIED as `staff_accounts_decoupled_from_profiles`:
      (a) `admin_team_members`: + `email text NOT NULL UNIQUE`, `display_name text NOT NULL`,
          `avatar_url text`; FK `user_id` repointed `profiles → auth.users(id) ON DELETE CASCADE`;
          `invited_by` FK dropped (plain uuid).
      (b) Drop profile FKs: `support_tickets.assigned_to`, `support_ticket_messages.sender_id`,
          `moderation_cases.resolved_by`, `moderation_actions.moderator_id`
          (keep `target_author_id` + `support_tickets.user_id` — those are app users).
      (c) `handle_new_user`: early-return when `new.raw_app_meta_data->>'role' = 'admin'`.
- [x] 3. `SupabaseAuthAdminClient` (admin/internal): POST `/auth/v1/invite` (carries `is_staff` +
      `display_name` in user metadata — the trigger keys on `is_staff` because it fires at INSERT,
      before app_metadata can be patched) → PUT `/auth/v1/admin/users/{id}` sets
      `app_metadata.role='admin'`; DELETE `/auth/v1/admin/users/{id}` on removal. Auth: service-role
      key via `supabase.secret-key` ← `SUPABASE_SECRET_KEY`. 422/400 from invite (email already an
      auth user) → 409 `StaffEmailInUseException`. Built with `RestClient.builder()` (Boot 4 has no
      auto-configured `RestClient.Builder` bean here).
- [x] 4. `AdminTeamService` rewritten: `POST /team {email, displayName, role}` (email lowercased,
      dup team email → 409); row starts `invited`, `AdminAccessService.touch` flips it `active` on
      first admin call (already did); `ProfileService` dependency gone; `TeamMemberDto` =
      {userId, email, displayName, avatarUrl, role, status, joinedAt, lastActiveAt}; DELETE removes
      row then auth user (row-first so a failed Supabase call rolls back).
- [x] 5. New `shared/staff/StaffDirectory` SPI (+`StaffRefDto`), implemented by admin
      (`StaffDirectoryImpl`) — support can't depend on admin (cycle). Wired into:
      `AdminModerationService.getCase` (action moderators + `resolvedBy` are `StaffRefDto` now;
      reporters/authors stay profiles) and `SupportServiceImpl.toDto` (`TicketDto.assignee` →
      `StaffRefDto`; `TicketMessageDto` gained `staffSender`, `sender` is null for staff messages).
      Assignee updates validate team membership (`InvalidAssigneeException`, 400) since the DB FK
      is gone.
- [x] 6. `BannedUserInterceptor`/`BanCache`: missing profile already reads as not banned — no change.
- [x] 7. Docs updated (admin/support/shared READMEs, CONTEXT.md, ADMIN_DASHBOARD_PROGRESS.md
      superseded-note) + `./mvnw test` green 3/3 (ModularityTests + live-schema validation).

**DONE 2026-07-12.** Frontend notes: dashboard Team page now posts `{email, display_name, role}`
and renders `display_name`/`email`; the invite email lands the invitee on the site URL — the
dashboard needs an accept-invite (set password) page. Staff message senders arrive as
`staff_sender` (snake_case JSON) on ticket messages; user app can render staff replies with
`staff_sender.display_name` or a generic "Support".

## Setup (owner bootstrap — one time)

The owner can't invite himself through the API (no admin exists yet). With a NEW email (not the
app account's):
1. Supabase dashboard → Auth → Add user → create the user; set user metadata
   `{"is_staff": true}` **at creation** if the dashboard allows it, otherwise verify no `profiles`
   row got created (delete it if so); then set `app_metadata` `{"role": "admin"}`.
2. `insert into admin_team_members (user_id, role, status, email, display_name) values
   ('<new-auth-uuid>', 'owner', 'active', '<email>', '<name>');`
3. Everyone else joins via dashboard → Team → invite by email.
