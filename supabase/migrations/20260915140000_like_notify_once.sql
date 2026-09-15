-- Notify an author about a given liker once, ever — for posts, forum threads and forum replies.
--
-- Same rule and same shape as 20260915120000_post_repost_notify_once.sql. A like row lives only while
-- the like does (unlike deletes it, which keeps likes_count and "you liked this" truthful), so the
-- like tables can't tell a first like from like → unlike → like, and each re-like used to notify the
-- author again. These ledgers remember that the author was told about this liker and deliberately
-- outlive the like. The backend inserts into them with ON CONFLICT DO NOTHING and publishes the
-- notification only when a row was actually inserted.
--
-- Comment likes are not covered: they never notify.
--
-- Deploy order: apply this BEFORE deploying the backend build that writes to these tables. There are
-- no JPA entities, so that build would start fine without them — but every like would then fail with
-- a 500. The currently running build ignores the tables.

begin;

-- 1. Ledgers. One per like table, with real FKs so deleting the content or the account cleans up.
create table public.post_like_notifications (
    post_id    uuid        not null references public.posts (id) on delete cascade,
    user_id    uuid        not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint post_like_notifications_pkey primary key (post_id, user_id)
);

create table public.forum_thread_like_notifications (
    thread_id  uuid        not null references public.forum_threads (id) on delete cascade,
    user_id    uuid        not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint forum_thread_like_notifications_pkey primary key (thread_id, user_id)
);

-- Named after forum_post_likes, whose post_id references forum_thread_replies (the table was renamed;
-- the likes table and its column kept the old name).
create table public.forum_post_like_notifications (
    post_id    uuid        not null references public.forum_thread_replies (id) on delete cascade,
    user_id    uuid        not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint forum_post_like_notifications_pkey primary key (post_id, user_id)
);

-- The backend connects with a BYPASSRLS role; clients never touch these tables directly.
alter table public.post_like_notifications enable row level security;
alter table public.forum_thread_like_notifications enable row level security;
alter table public.forum_post_like_notifications enable row level security;
revoke select, insert, update, delete on public.post_like_notifications from anon, authenticated;
revoke select, insert, update, delete on public.forum_thread_like_notifications from anon, authenticated;
revoke select, insert, update, delete on public.forum_post_like_notifications from anon, authenticated;

comment on table public.post_like_notifications is
    'One row per (post, liker) whose like already notified the post''s author. Outlives the like (unlike keeps it), so liking again never re-notifies.';
comment on table public.forum_thread_like_notifications is
    'One row per (thread, liker) whose like already notified the thread''s author. Outlives the like (unlike keeps it), so liking again never re-notifies.';
comment on table public.forum_post_like_notifications is
    'One row per (forum reply, liker) whose like already notified the reply''s author. Outlives the like (unlike keeps it), so liking again never re-notifies.';

-- 2. Backfill from current likes: those authors were already notified.
insert into public.post_like_notifications (post_id, user_id, created_at)
select post_id, user_id, created_at from public.post_likes
on conflict do nothing;

insert into public.forum_thread_like_notifications (thread_id, user_id, created_at)
select thread_id, user_id, created_at from public.forum_thread_likes
on conflict do nothing;

insert into public.forum_post_like_notifications (post_id, user_id, created_at)
select post_id, user_id, created_at from public.forum_post_likes
on conflict do nothing;

-- 3. Backfill from past notifications: people who liked, got the author notified, then unliked. Their
--    notification is the only trace left. Joins drop notifications whose target or actor is gone.
insert into public.post_like_notifications (post_id, user_id, created_at)
select p.id, pr.id, min(n.created_at)
  from public.notifications n
  join public.posts p on p.id::text = n.payload ->> 'post_id'
  join public.profiles pr on pr.id::text = n.payload ->> 'actor_id'
 where n.type = 'post_like'
 group by p.id, pr.id
on conflict do nothing;

insert into public.forum_thread_like_notifications (thread_id, user_id, created_at)
select t.id, pr.id, min(n.created_at)
  from public.notifications n
  join public.forum_threads t on t.id::text = n.payload ->> 'thread_id'
  join public.profiles pr on pr.id::text = n.payload ->> 'actor_id'
 where n.type = 'forum_thread_like'
 group by t.id, pr.id
on conflict do nothing;

insert into public.forum_post_like_notifications (post_id, user_id, created_at)
select r.id, pr.id, min(n.created_at)
  from public.notifications n
  join public.forum_thread_replies r on r.id::text = n.payload ->> 'reply_id'
  join public.profiles pr on pr.id::text = n.payload ->> 'actor_id'
 where n.type = 'forum_reply_like'
 group by r.id, pr.id
on conflict do nothing;

commit;
