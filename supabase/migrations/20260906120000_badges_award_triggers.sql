-- Badges — unlocking a badge automatically when something happens, for a limited time.
--
-- Until now every badge was granted by hand from the dashboard: `pioneer` meant a staff member
-- typing a user_badges row per new account. Two things were missing, and both are added here as
-- *data* rather than as code, because both are things that change without a deploy:
--
--   * WHAT UNLOCKS IT — `award_trigger` names an event the backend reports ("this account was
--     created"). The module that witnessed the event calls BadgeService.awardForTrigger and never
--     names a badge; which badges that event unlocks is decided by the rows below. A second
--     signup badge is then a row in this table, not a code change and not a deploy.
--
--   * HOW LONG IT IS OFFERED — `earnable_from` / `earnable_until` bound the window in which the
--     trigger pays out. `pioneer` is a launch-year badge; without a window, withdrawing it on the
--     right day is a reminder in somebody's calendar, and a missed reminder means a "limited"
--     badge quietly handed out forever.
--
-- `is_available` keeps exactly the meaning it always had: a manual kill switch. The window is the
-- automatic half of the same question. A badge may be awarded when it is available AND the moment
-- being judged falls inside its window — see badges.is_available's comment and BadgeEntity.

begin;

-- ---------------------------------------------------------------------------
-- 1. What unlocks the badge
-- ---------------------------------------------------------------------------
-- NULL = nothing does; the badge is hand-granted from the dashboard. That is every badge that
-- exists today and will stay the normal case for badges that are a judgement call ("helped at the
-- meet"), so NULL costs a null-bitmap bit on those rows and nothing else.
--
-- A non-NULL value is a trigger code the backend knows, mirrored one-for-one by the BadgeTrigger
-- enum in the badges module. The CHECK is deliberate: a code the backend does not recognise would
-- match no event, so the badge would silently never unlock, and "the pioneer badge stopped being
-- awarded three weeks ago" is not a failure anybody notices in a log. Rejecting the write is the
-- loud version of the same rule.
--
-- Adding a trigger is therefore a migration plus a BadgeTrigger constant, released together — no
-- extra friction, because a new trigger already needs a Java call site to fire it. Adding a *badge*
-- on an existing trigger needs neither.
alter table public.badges
    add column if not exists award_trigger text;

alter table public.badges
    add constraint badges_award_trigger_known_ck check (
        award_trigger is null
        or award_trigger in ('account_created')
    );

comment on column public.badges.award_trigger is
    'The event that unlocks this badge automatically, matching the BadgeTrigger enum in the backend. NULL = hand-granted only. Known codes: account_created. Adding one means extending badges_award_trigger_known_ck and BadgeTrigger in the same release.';

-- ---------------------------------------------------------------------------
-- 2. How long it is offered
-- ---------------------------------------------------------------------------
-- The half-open interval [earnable_from, earnable_until). NULL on either side means unbounded, so
-- a permanent badge leaves both NULL and behaves exactly as it does today.
--
-- `earnable_until` is the point: a limited-time badge retires itself on the date, with nobody
-- having to remember. `earnable_from` is the cheap other half — it lets a badge be staged with its
-- artwork and copy in place and start awarding on its own at launch time, instead of somebody
-- flipping is_available at midnight.
--
-- The window is judged against the moment the achievement happened, not against now(). For
-- `pioneer` that is the account's creation date, which is what makes the badge fair to someone who
-- signed up two days before the cutoff and finished onboarding a week after it.
alter table public.badges
    add column if not exists earnable_from  timestamptz,
    add column if not exists earnable_until timestamptz;

-- A window that ends before it starts can never award anything — that is a typo, not a
-- configuration, and it would present as a badge nobody can earn and no error anywhere.
alter table public.badges
    add constraint badges_earnable_window_ck check (
        earnable_from is null
        or earnable_until is null
        or earnable_from < earnable_until
    );

comment on column public.badges.earnable_from is
    'Start of the window in which this badge may be awarded automatically, inclusive. NULL = no start bound. Lets a badge be staged before it opens.';
comment on column public.badges.earnable_until is
    'End of the window in which this badge may be awarded automatically, exclusive. NULL = no end bound. This is how a limited-time badge retires itself; the users already holding it are untouched.';

comment on column public.badges.is_available is
    'false = retired: cannot be awarded and is hidden from the catalogue, but stays readable on the profiles that already hold it. The manual half of awardability — earnable_from/earnable_until are the automatic half, and a badge must satisfy both.';

-- ---------------------------------------------------------------------------
-- 3. Pioneer becomes a rule
-- ---------------------------------------------------------------------------
-- It was hand-granted because "was here early" read as a judgement call. With a launch date and a
-- cutoff it is not one any more: it is "your account was created in the first year", which the
-- backend can evaluate exactly, for every account, without anybody being asked.
--
-- The window is [launch, launch + 1 year) — Tweakd launched 2026-09-06, so the badge stops being
-- awarded at 2027-09-06T00:00:00Z. Moving that date later is an UPDATE (or an edit on the
-- dashboard's badge page), never a deploy.
--
-- earnable_from is set to the launch date rather than left NULL on purpose: it is the honest
-- statement of the offer, and it means an account whose creation date predates launch — a seed or
-- test row — does not quietly qualify.
--
-- Guarded on existence so this migration is safe on an environment where the row was never seeded
-- (a fresh branch database, say) rather than silently updating nothing on the real one.
do $$
begin
    if not exists (select 1 from public.badges where id = 'pioneer') then
        raise warning 'badges: no ''pioneer'' row to configure — skipping. Seed it, then set award_trigger/earnable_from/earnable_until by hand.';
    else
        update public.badges
           set award_trigger  = 'account_created',
               earnable_from  = timestamptz '2026-09-06 00:00:00+00',
               earnable_until = timestamptz '2027-09-06 00:00:00+00'
         where id = 'pioneer';
    end if;
end $$;

-- No index on award_trigger. `badges` is a curated catalogue of a handful of rows that every
-- lookup here reads in full anyway; an index would be maintenance on every dashboard edit to save
-- a sequential scan of one page. The same reasoning the hardening migration used for declining
-- (user_id, created_at) on user_badges.

commit;
