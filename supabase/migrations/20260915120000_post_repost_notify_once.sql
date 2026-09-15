-- Notify a post's author about a given reposter once, ever.
--
-- A repost row lives only while the repost does: undoing deletes it (that is what keeps shares_count,
-- the feed boost, "reposted by" and the Reposts tab truthful). So "was this a first repost?" can't be
-- answered from post_shares — repost → undo → repost looked like a first repost every time and
-- re-notified the author. This ledger remembers that the author was told about this reposter, and
-- deliberately outlives the repost. The backend inserts into it with ON CONFLICT DO NOTHING and
-- publishes the notification only when a row was actually inserted.
--
-- Deploy order: apply this BEFORE deploying the backend build that writes to it. There is no JPA
-- entity, so that build would start fine without the table — but every repost would then fail with
-- a 500. The currently running build ignores the table.

begin;

create table public.post_repost_notifications (
    post_id    uuid        not null references public.posts (id) on delete cascade,
    user_id    uuid        not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint post_repost_notifications_pkey primary key (post_id, user_id)
);

-- The backend connects with a BYPASSRLS role; clients never touch this table directly.
alter table public.post_repost_notifications enable row level security;
revoke select, insert, update, delete on public.post_repost_notifications from anon, authenticated;

comment on table public.post_repost_notifications is
    'One row per (post, reposter) whose repost already notified the post''s author. Outlives the repost (undo keeps it), so reposting again never re-notifies.';

-- Backfill 1: everyone currently reposting was (or, before the notification toggle, would have been)
-- notified already.
insert into public.post_repost_notifications (post_id, user_id, created_at)
select post_id, user_id, created_at
  from public.post_shares
on conflict do nothing;

-- Backfill 2: people who reposted, got the author notified, then undid. Their notification is the
-- only trace left. Joins drop notifications whose post or actor no longer exists.
insert into public.post_repost_notifications (post_id, user_id, created_at)
select p.id, pr.id, min(n.created_at)
  from public.notifications n
  join public.posts p on p.id::text = n.payload ->> 'post_id'
  join public.profiles pr on pr.id::text = n.payload ->> 'actor_id'
 where n.type = 'post_share'
 group by p.id, pr.id
on conflict do nothing;

commit;
