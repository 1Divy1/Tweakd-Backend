-- Business verification from the admin dashboard.
--
-- Until now the only way to move a business_accounts row from 'pending' to 'verified' was a
-- hand-written UPDATE in the Supabase SQL editor, and a rejection had nowhere to record why. The
-- dashboard's review queue needs three things the table does not have: the reason a business was
-- turned down (the only thing anyone could act on afterwards), and who decided, when.
--
-- reviewed_by holds a *staff* auth user id (admin_team_members.user_id), which is not a profiles
-- row — so no FK, exactly like car_event_contests.finished_by. Nothing joins it; it is an audit
-- breadcrumb read by the dashboard through the admin API.

alter table public.business_accounts
    add column if not exists rejection_reason text,
    add column if not exists reviewed_by      uuid,
    add column if not exists reviewed_at      timestamptz;

comment on column public.business_accounts.rejection_reason is
    'Why a verification request was turned down. Set when verification_status = ''rejected''; left in place afterwards as history.';
comment on column public.business_accounts.reviewed_by is
    'Staff auth user id (admin_team_members.user_id) of whoever last decided this business''s verification or active status. No FK: staff accounts have no profiles row.';
comment on column public.business_accounts.reviewed_at is
    'When that decision was taken.';

-- The review queue reads "everything pending, oldest first". A partial index keeps that page (and
-- the sidebar's pending count) off a sequential scan as the table grows, and costs nothing while
-- the queue is empty, which is its steady state.
create index if not exists business_accounts_pending_review_idx
    on public.business_accounts (created_at asc, id asc)
    where verification_status = 'pending';

-- Same reasoning for the dashboard's roadmap badge: count of un-triaged community requests.
create index if not exists feedback_feed_messages_new_idx
    on public.feedback_feed_messages (created_at desc, id desc)
    where status = 'sent' and is_deleted = false;
